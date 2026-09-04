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
