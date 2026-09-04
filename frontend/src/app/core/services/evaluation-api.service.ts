import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { EvaluationResult, EvaluationRun, TestQuery } from '../models/rag.models';

interface ApiResponse<T> {
  status: string;
  message?: string;
  [key: string]: unknown;
}

@Injectable({ providedIn: 'root' })
export class EvaluationApiService {
  private readonly base = `${API_BASE_URL}/evaluation`;

  constructor(private http: HttpClient) {}

  seedTestData(): Observable<ApiResponse<never>> {
    return this.http.post<ApiResponse<never>>(`${this.base}/seed-test-data`, {});
  }

  getTestQueries(): Observable<{ status: string; queries: TestQuery[]; count: number }> {
    return this.http.get<{ status: string; queries: TestQuery[]; count: number }>(`${this.base}/test-queries`);
  }

  evaluateQuery(queryId: number): Observable<{ status: string; result: EvaluationResult }> {
    return this.http.post<{ status: string; result: EvaluationResult }>(
      `${this.base}/evaluate-query/${queryId}`, {});
  }

  batchEvaluate(runName: string): Observable<{ status: string; run: EvaluationRun; message: string }> {
    const params = new HttpParams().set('runName', runName);
    return this.http.post<{ status: string; run: EvaluationRun; message: string }>(
      `${this.base}/batch-evaluate`, {}, { params });
  }

  getRuns(): Observable<{ status: string; runs: EvaluationRun[]; count: number }> {
    return this.http.get<{ status: string; runs: EvaluationRun[]; count: number }>(`${this.base}/runs`);
  }

  getAllResults(): Observable<{ status: string; results: EvaluationResult[]; count: number }> {
    return this.http.get<{ status: string; results: EvaluationResult[]; count: number }>(`${this.base}/results`);
  }

  getResultsForQuery(queryId: number): Observable<{ status: string; results: EvaluationResult[]; count: number }> {
    return this.http.get<{ status: string; results: EvaluationResult[]; count: number }>(
      `${this.base}/results/${queryId}`);
  }
}
