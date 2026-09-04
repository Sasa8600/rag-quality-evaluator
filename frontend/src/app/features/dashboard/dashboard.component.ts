import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { RagApiService } from '../../core/services/rag-api.service';
import { EvaluationApiService } from '../../core/services/evaluation-api.service';
import { EvaluationRun } from '../../core/models/rag.models';
import { MetricBarComponent } from '../../shared/metric-bar/metric-bar.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MetricBarComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit {
  readonly loading = signal(true);
  readonly documentCount = signal(0);
  readonly testQueryCount = signal(0);
  readonly runs = signal<EvaluationRun[]>([]);
  readonly latestRun = signal<EvaluationRun | null>(null);
  readonly error = signal<string | null>(null);

  constructor(private ragApi: RagApiService, private evalApi: EvaluationApiService) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      documents: this.ragApi.listDocuments(),
      queries: this.evalApi.getTestQueries(),
      runs: this.evalApi.getRuns(),
    }).subscribe({
      next: ({ documents, queries, runs }) => {
        this.documentCount.set((documents['count'] as number) ?? 0);
        this.testQueryCount.set(queries.count ?? 0);
        const sorted = runs.runs ?? [];
        this.runs.set(sorted);
        this.latestRun.set(sorted.length > 0 ? sorted[0] : null);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach backend. Is the Spring Boot app running on :8080?');
        this.loading.set(false);
      },
    });
  }
}
