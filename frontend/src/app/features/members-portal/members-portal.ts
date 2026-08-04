// features/members-portal/members-portal.ts
import {Component, inject, OnInit, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {AuthService} from '../../core/auth/services/auth-service';
import {DocumentService} from '../../core/auth/services/document-service';
import {DocumentItem, DocumentVisibility} from '../../core/auth/models/document.model';

@Component({
  selector: 'app-members-portal',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './members-portal.html',
})
export class MembersPortal implements OnInit {
  documents = signal<DocumentItem[]>([]);
  loading = signal(true);
  showUploadForm = signal(false);
  uploading = signal(false);
  selectedFile = signal<File | null>(null);
  errorMessage = signal<string | null>(null);
  downloadingId = signal<number | null>(null);
  deletingId = signal<number | null>(null);
  private documentService = inject(DocumentService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  uploadForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: [''],
    visibility: ['' as DocumentVisibility | '', Validators.required],
  });

  isAdmin = () => this.authService.isAdmin();

  ngOnInit(): void {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.loading.set(true);
    this.documentService.getAll().subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  onUpload(): void {
    const file = this.selectedFile();
    if (!file || this.uploadForm.invalid || this.uploading()) {
      this.uploadForm.markAllAsTouched();
      return;
    }

    this.uploading.set(true);
    this.errorMessage.set(null);

    const {title, description, visibility} = this.uploadForm.getRawValue();
    this.documentService.upload(file, title, description, visibility as DocumentVisibility).subscribe({
      next: () => {
        this.uploading.set(false);
        this.showUploadForm.set(false);
        this.uploadForm.reset();
        this.selectedFile.set(null);
        this.loadDocuments();
      },
      error: () => {
        this.errorMessage.set("Une erreur est survenue lors de l'envoi du fichier.");
        this.uploading.set(false);
      },
    });
  }

  onDownload(doc: DocumentItem): void {
    this.downloadingId.set(doc.id);
    this.documentService.download(doc.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = doc.originalFilename;
        anchor.click();
        window.URL.revokeObjectURL(url);
        this.downloadingId.set(null);
      },
      error: () => this.downloadingId.set(null),
    });
  }

  onDelete(doc: DocumentItem): void {
    if (!confirm(`Supprimer le document "${doc.title}" ? Cette action est irréversible.`)) {
      return;
    }

    this.deletingId.set(doc.id);
    this.documentService.delete(doc.id).subscribe({
      next: () => {
        this.documents.update(list => list.filter(d => d.id !== doc.id));
        this.deletingId.set(null);
      },
      error: () => this.deletingId.set(null),
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }
}
