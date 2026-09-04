import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { Document, QueryResponse } from '../models/rag.models';

interface ApiListResponse<T> {
  status: string;
  count: number;
  message?: string;
  [key: string]: unknown;
}

interface ApiMessageResponse {
  status: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class RagApiService {
  private readonly base = `${API_BASE_URL}/rag`;

  constructor(private http: HttpClient) {}

  health(): Observable<{ status: string; service: string }> {
    return this.http.get<{ status: string; service: string }>(`${this.base}/health`);
  }

  listDocuments(): Observable<ApiListResponse<Document[]>> {
    return this.http.get<ApiListResponse<Document[]>>(`${this.base}/documents`);
  }

  ingestDocument(name: string, content: string, source?: string): Observable<ApiMessageResponse> {
    return this.http.post<ApiMessageResponse>(`${this.base}/ingest`, { name, content, source });
  }

  deleteDocument(id: number): Observable<ApiMessageResponse> {
    return this.http.delete<ApiMessageResponse>(`${this.base}/documents/${id}`);
  }

  query(query: string): Observable<QueryResponse> {
    return this.http.post<QueryResponse>(`${this.base}/query`, { query });
  }
}
