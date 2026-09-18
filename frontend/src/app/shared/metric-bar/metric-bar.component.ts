import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-metric-bar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metric-bar.component.html',
  styleUrl: './metric-bar.component.css',
})
export class MetricBarComponent {
  @Input() label = '';
  @Input() value: number | null | undefined = 0;

  // null/undefined means "no ground truth to compute this against" (see EvaluationResult),
  // which is a different thing from an actual 0.00 score — the two must not render the same way.
  get isAvailable(): boolean {
    return this.value !== null && this.value !== undefined;
  }

  get pct(): number {
    const v = this.value ?? 0;
    return Math.max(0, Math.min(1, v)) * 100;
  }

  get level(): 'low' | 'mid' | 'high' {
    const v = this.value ?? 0;
    if (v >= 0.7) return 'high';
    if (v >= 0.4) return 'mid';
    return 'low';
  }
}
