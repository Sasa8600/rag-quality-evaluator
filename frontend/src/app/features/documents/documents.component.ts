import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RagApiService } from '../../core/services/rag-api.service';
import { Document } from '../../core/models/rag.models';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.css',
})
export class DocumentsComponent implements OnInit {
  readonly documents = signal<Document[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  name = '';
  content = '';
  source = '';
  chunkSize: number | null = null;
  overlap: number | null = null;
  showAdvanced = false;

  readonly pdfUploading = signal(false);
  selectedFile: File | null = null;

  constructor(private ragApi: RagApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.ragApi.listDocuments().subscribe({
      next: (res) => {
        this.documents.set((res['documents'] as Document[]) ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load documents. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    if (!this.name.trim() || !this.content.trim()) {
      this.error.set('Name and content are required.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.successMsg.set(null);
    this.ragApi.ingestDocument(this.name.trim(), this.content.trim(), this.source.trim() || undefined,
      this.chunkSize ?? undefined, this.overlap ?? undefined).subscribe({
      next: (res) => {
        this.successMsg.set(res.message ?? 'Document ingested.');
        this.name = '';
        this.content = '';
        this.source = '';
        this.submitting.set(false);
        this.load();
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Failed to ingest document.');
        this.submitting.set(false);
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  uploadPdf(): void {
    if (!this.selectedFile) {
      this.error.set('Choose a PDF file first.');
      return;
    }
    this.pdfUploading.set(true);
    this.error.set(null);
    this.successMsg.set(null);
    this.ragApi.ingestPdf(this.selectedFile, this.name.trim() || undefined, this.source.trim() || undefined,
      this.chunkSize ?? undefined, this.overlap ?? undefined).subscribe({
      next: (res) => {
        this.successMsg.set(res.message ?? 'PDF ingested.');
        this.selectedFile = null;
        this.pdfUploading.set(false);
        this.load();
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Failed to ingest PDF.');
        this.pdfUploading.set(false);
      },
    });
  }

  remove(id: number): void {
    if (!confirm('Delete this document and its chunks/embeddings?')) return;
    this.ragApi.deleteDocument(id).subscribe({
      next: () => this.load(),
      error: () => this.error.set('Failed to delete document.'),
    });
  }
}
