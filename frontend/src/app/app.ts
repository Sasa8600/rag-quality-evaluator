import { Component, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { RagApiService } from './core/services/rag-api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  protected readonly title = signal('RAG Quality Evaluator');
  protected readonly backendUp = signal<boolean | null>(null);

  constructor(private ragApi: RagApiService) {}

  ngOnInit(): void {
    this.ragApi.health().subscribe({
      next: () => this.backendUp.set(true),
      error: () => this.backendUp.set(false),
    });
  }
}
