import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { map, finalize } from 'rxjs';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

/** Paths where duplicate POSTs can corrupt financial state if retried without a key. */
const IDEMPOTENT_POST = /\/api\/v1\/clients\/[^/]+\/(expenses(\/[^/]+\/(approve|void))?|income(\/[^/]+\/(approve|void))?|bank\/(imports|transactions\/[^/]+\/confirm)|documents\/[^/]+\/review\/accept|periods\/[^/]+\/close)(?:\?|$)/;

@Injectable({ providedIn: 'root' })
export class ApiService {
  /** In-flight stable keys so double-clicks / retries reuse the same Idempotency-Key. */
  private readonly inflightKeys = new Map<string, string>();

  constructor(private http: HttpClient) {}

  list<T>(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.get<T[] | PageResponse<T>>(url, params).pipe(
      map((response) => Array.isArray(response) ? response : (response.content ?? []))
    );
  }

  get<T>(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.http.get<T>(url, { params: this.toParams(params), withCredentials: true });
  }

  post<T>(url: string, body?: unknown) {
    const payload = body ?? {};
    const headers = this.idempotencyHeaders(url, payload);
    return this.http.post<T>(url, payload, { headers, withCredentials: true }).pipe(
      finalize(() => this.releaseIdempotencyKey(url, payload))
    );
  }

  put<T>(url: string, body: unknown) {
    return this.http.put<T>(url, body, { withCredentials: true });
  }

  delete<T>(url: string) {
    return this.http.delete<T>(url, { withCredentials: true });
  }

  upload<T>(url: string, file: File, extra?: Record<string, string>) {
    const data = new FormData();
    data.append('file', file);
    Object.entries(extra ?? {}).forEach(([key, value]) => data.append(key, value));
    const headers = this.idempotencyHeaders(url, `upload:${file.name}:${file.size}:${file.lastModified}`);
    return this.http.post<T>(url, data, { headers, withCredentials: true }).pipe(
      finalize(() => this.releaseIdempotencyKey(url, `upload:${file.name}:${file.size}:${file.lastModified}`))
    );
  }

  download(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.http.get(url, { params: this.toParams(params), responseType: 'blob', withCredentials: true });
  }

  downloadAttachment(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.http.get(url, {
      params: this.toParams(params),
      responseType: 'blob',
      observe: 'response',
      withCredentials: true
    }).pipe(
      map((response) => ({
        blob: response.body as Blob,
        filename: attachmentFilename(response.headers.get('Content-Disposition'), 'export')
      }))
    );
  }

  private idempotencyHeaders(url: string, body: unknown): HttpHeaders | undefined {
    const path = url.split('?')[0];
    if (!IDEMPOTENT_POST.test(path)) {
      return undefined;
    }
    const fingerprint = `POST ${path} ${stableSerialize(body)}`;
    let key = this.inflightKeys.get(fingerprint);
    if (!key) {
      key = crypto.randomUUID();
      this.inflightKeys.set(fingerprint, key);
    }
    return new HttpHeaders({ 'Idempotency-Key': key });
  }

  private releaseIdempotencyKey(url: string, body: unknown): void {
    const path = url.split('?')[0];
    if (!IDEMPOTENT_POST.test(path)) {
      return;
    }
    this.inflightKeys.delete(`POST ${path} ${stableSerialize(body)}`);
  }

  private toParams(params?: Record<string, string | number | boolean | undefined>): HttpParams {
    let httpParams = new HttpParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return httpParams;
  }
}

function stableSerialize(body: unknown): string {
  if (typeof body === 'string') {
    return body;
  }
  try {
    return JSON.stringify(body ?? {});
  } catch {
    return String(body);
  }
}

function attachmentFilename(header: string | null, fallback: string): string {
  if (!header) {
    return fallback;
  }
  const utfMatch = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utfMatch?.[1]) {
    try {
      return decodeURIComponent(utfMatch[1].trim());
    } catch {
      return utfMatch[1].trim();
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain?.[1]?.trim() || fallback;
}
