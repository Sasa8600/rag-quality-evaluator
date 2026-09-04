import { Injectable, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';
import { WS_BASE_URL } from './api.config';
import { EvaluationProgress } from '../models/rag.models';

@Injectable({ providedIn: 'root' })
export class ProgressWsService implements OnDestroy {
  private socket: WebSocket | null = null;
  private readonly progress$ = new Subject<EvaluationProgress>();
  private readonly connected$ = new Subject<boolean>();

  readonly onProgress = this.progress$.asObservable();
  readonly onConnectionChange = this.connected$.asObservable();

  connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }
    try {
      this.socket = new WebSocket(`${WS_BASE_URL}/ws/evaluation-progress`);
      this.socket.onopen = () => this.connected$.next(true);
      this.socket.onclose = () => this.connected$.next(false);
      this.socket.onerror = () => this.connected$.next(false);
      this.socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as EvaluationProgress;
          this.progress$.next(data);
        } catch {
          // ignore malformed message
        }
      };
    } catch {
      this.connected$.next(false);
    }
  }

  disconnect(): void {
    this.socket?.close();
    this.socket = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
