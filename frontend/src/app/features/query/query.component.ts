import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RagApiService } from '../../core/services/rag-api.service';
import { QueryResponse } from '../../core/models/rag.models';

@Component({
  selector: 'app-query',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './query.component.html',
  styleUrl: './query.component.css',
})
export class QueryComponent {
  query = '';
  readonly loading = signal(false);
  readonly result = signal<QueryResponse | null>(null);
  readonly error = signal<string | null>(null);

  constructor(private ragApi: RagApiService) {}

  ask(): void {
    if (!this.query.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.ragApi.query(this.query.trim()).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Query failed. Check documents are ingested and Ollama is running.');
        this.loading.set(false);
      },
    });
  }
}
