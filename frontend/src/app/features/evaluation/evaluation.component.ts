import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { EvaluationApiService } from '../../core/services/evaluation-api.service';
import { ProgressWsService } from '../../core/services/progress-ws.service';
import { EvaluationResult, EvaluationRun, TestQuery } from '../../core/models/rag.models';
import { MetricBarComponent } from '../../shared/metric-bar/metric-bar.component';

@Component({
  selector: 'app-evaluation',
  standalone: true,
  imports: [CommonModule, FormsModule, MetricBarComponent],
  templateUrl: './evaluation.component.html',
  styleUrl: './evaluation.component.css',
})
export class EvaluationComponent implements OnInit, OnDestroy {
  readonly testQueries = signal<TestQuery[]>([]);
  readonly runs = signal<EvaluationRun[]>([]);
  readonly results = signal<EvaluationResult[]>([]);
  readonly seeding = signal(false);
  readonly running = signal(false);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  readonly progressCurrent = signal(0);
  readonly progressTotal = signal(0);
  readonly progressStatus = signal<string>('idle');
  readonly lastQueryText = signal<string | null>(null);
  readonly lastRagScore = signal<number | null>(null);

  runName = 'batch-eval-run';

  private wsSub?: Subscription;

  constructor(private evalApi: EvaluationApiService, private ws: ProgressWsService) {}

  ngOnInit(): void {
    this.ws.connect();
    this.wsSub = this.ws.onProgress.subscribe((p) => {
      if (p.type !== 'evaluation-progress') return;
      this.progressCurrent.set(Math.max(p.current, 0));
      this.progressTotal.set(Math.max(p.total, 0));
      this.progressStatus.set(p.status);
      if (p.lastQuery) this.lastQueryText.set(p.lastQuery);
      if (p.lastRagScore !== undefined) this.lastRagScore.set(p.lastRagScore);
      if (p.status === 'complete') {
        this.running.set(false);
        this.load();
      }
      if (p.status === 'error') {
        this.running.set(false);
        this.error.set('Batch evaluation failed. Check backend logs.');
      }
    });
    this.load();
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
  }

  load(): void {
    this.loading.set(true);
    this.evalApi.getTestQueries().subscribe({
      next: (res) => this.testQueries.set(res.queries ?? []),
      error: () => this.error.set('Failed to load test queries.'),
    });
    this.evalApi.getRuns().subscribe({
      next: (res) => this.runs.set(res.runs ?? []),
    });
    this.evalApi.getAllResults().subscribe({
      next: (res) => {
        this.results.set(res.results ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  seed(): void {
    this.seeding.set(true);
    this.error.set(null);
    this.successMsg.set(null);
    this.evalApi.seedTestData().subscribe({
      next: (res) => {
        this.successMsg.set((res['message'] as string) ?? 'Test data seeded.');
        this.seeding.set(false);
        this.load();
      },
      error: () => {
        this.error.set('Failed to seed test data.');
        this.seeding.set(false);
      },
    });
  }

  runBatch(): void {
    if (this.testQueries().length === 0) {
      this.error.set('Seed test data first.');
      return;
    }
    this.running.set(true);
    this.error.set(null);
    this.successMsg.set(null);
    this.progressCurrent.set(0);
    this.progressTotal.set(this.testQueries().length);
    this.progressStatus.set('started');
    this.lastQueryText.set(null);
    this.lastRagScore.set(null);

    this.evalApi.batchEvaluate(this.runName.trim() || 'batch-eval-run').subscribe({
      next: (res) => {
        this.successMsg.set(res.message);
        // WebSocket 'complete' event also triggers reload; this covers the case
        // where the socket missed a message.
        this.running.set(false);
        this.load();
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Batch evaluation failed.');
        this.running.set(false);
      },
    });
  }

  get progressPct(): number {
    if (this.progressTotal() === 0) return 0;
    return Math.round((this.progressCurrent() / this.progressTotal()) * 100);
  }
}
