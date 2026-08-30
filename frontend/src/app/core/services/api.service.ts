import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map } from 'rxjs';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  list<T>(url: string, params?: Record<string, string | number | boolean | undefined>) {
    return this.get<T[] | PageResponse<T>>(url, params).pipe(
      map((response) => Array.isArray(response) ? response : (response.content ?? []))
    );
  }

  get<T>(url: string, params?: Record<string, string | number | boolean | undefined>) {
    let httpParams = new HttpParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<T>(url, { params: httpParams });
  }

  post<T>(url: string, body?: unknown) {
    return this.http.post<T>(url, body ?? {});
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
    return this.http.post<T>(url, data);
  }

  download(url: string, params?: Record<string, string>) {
    let httpParams = new HttpParams();
    Object.entries(params ?? {}).forEach(([key, value]) => httpParams = httpParams.set(key, value));
    return this.http.get(url, { params: httpParams, responseType: 'blob' });
  }
}
