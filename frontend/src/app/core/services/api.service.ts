import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { map } from 'rxjs';

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
  constructor(private http: HttpClient) {}

  list<T>(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.get<T[] | PageResponse<T>>(url, params).pipe(
      map((response) => Array.isArray(response) ? response : (response.content ?? []))
    );
  }

  get<T>(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.http.get<T>(url, { params: this.toParams(params) });
  }

  post<T>(url: string, body?: unknown) {
    return this.http.post<T>(url, body ?? {}, { headers: this.idempotencyHeaders(url) });
  }

  put<T>(url: string, body: unknown) {
    return this.http.put<T>(url, body);
  }

  delete<T>(url: string) {
    return this.http.delete<T>(url);
  }

  upload<T>(url: string, file: File, extra?: Record<string, string>) {
    const data = new FormData();
    data.append('file', file);
    Object.entries(extra ?? {}).forEach(([key, value]) => data.append(key, value));
    return this.http.post<T>(url, data, { headers: this.idempotencyHeaders(url) });
  }

  download(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.http.get(url, { params: this.toParams(params), responseType: 'blob' });
  }

  downloadAttachment(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.http.get(url, { params: this.toParams(params), responseType: 'blob', observe: 'response' }).pipe(
      map((response) => ({
        blob: response.body as Blob,
        filename: attachmentFilename(response.headers.get('Content-Disposition'), 'export')
      }))
    );
  }

  private idempotencyHeaders(url: string): HttpHeaders | undefined {
    if (!IDEMPOTENT_POST.test(url.split('?')[0])) {
      return undefined;
    }
    return new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() });
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

function attachmentFilename(header: string | null, fallback: string): string {
  if (!header) {
    return fallback;
  }
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1]);
    } catch {
      return fallback;
    }
  }
  const quoted = /filename="([^"]+)"/i.exec(header);
  if (quoted?.[1]) {
    return quoted[1];
  }
  const plain = /filename=([^;]+)/i.exec(header);
  return plain?.[1]?.trim() || fallback;
}
