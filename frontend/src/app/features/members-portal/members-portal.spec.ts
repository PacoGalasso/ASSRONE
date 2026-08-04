import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { MembersPortal } from './members-portal';
import { DocumentService } from '../../core/auth/services/document-service';
import { AuthService } from '../../core/auth/services/auth-service';
import { DocumentItem } from '../../core/auth/models/document.model';

describe('MembersPortal', () => {
  let component: MembersPortal;
  let fixture: ComponentFixture<MembersPortal>;
  let documentService: {
    getAll: ReturnType<typeof vi.fn>;
    upload: ReturnType<typeof vi.fn>;
    download: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let authService: { isAdmin: ReturnType<typeof vi.fn> };

  const membersDoc: DocumentItem = {
    id: 1, title: 'Statuts', description: '', originalFilename: 'statuts.pdf',
    contentType: 'application/pdf', fileSize: 1024, uploadedBy: 'admin@assrone.ch',
    visibility: 'MEMBERS', uploadedAt: '2026-01-01T00:00:00',
  };
  const adminOnlyDoc: DocumentItem = {
    id: 2, title: 'Budget interne', description: '', originalFilename: 'budget.pdf',
    contentType: 'application/pdf', fileSize: 2048, uploadedBy: 'admin@assrone.ch',
    visibility: 'ADMIN_ONLY', uploadedAt: '2026-01-01T00:00:00',
  };

  beforeEach(async () => {
    documentService = {
      getAll: vi.fn().mockReturnValue(of([])),
      upload: vi.fn(),
      download: vi.fn(),
      delete: vi.fn(),
    };
    authService = {isAdmin: vi.fn().mockReturnValue(true)};

    await TestBed.configureTestingModule({
      imports: [MembersPortal],
      providers: [
        provideRouter([]),
        {provide: DocumentService, useValue: documentService},
        {provide: AuthService, useValue: authService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MembersPortal);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ===== Formulaire de dépôt : visibilité =====

  it('renders exactly two visibility options', () => {
    // #given
    component.showUploadForm.set(true);
    fixture.detectChanges();

    // #then
    const radios: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('input[formControlName="visibility"]');
    expect(radios.length).toBe(2);
    const values = Array.from(radios).map(r => r.value);
    expect(values).toEqual(['MEMBERS', 'ADMIN_ONLY']);
  });

  it('marks the form invalid when no visibility is chosen', () => {
    // #then
    expect(component.uploadForm.controls.visibility.invalid).toBe(true);
  });

  it('does not call upload when no visibility is chosen', () => {
    // #given
    component.selectedFile.set(new File(['contenu'], 'doc.pdf'));
    component.uploadForm.patchValue({title: 'Titre', description: '', visibility: ''});

    // #when
    component.onUpload();

    // #then
    expect(documentService.upload).not.toHaveBeenCalled();
  });

  it('sends MEMBERS as the technical visibility value on submit', () => {
    // #given
    documentService.upload.mockReturnValue(of({} as DocumentItem));
    component.selectedFile.set(new File(['contenu'], 'doc.pdf'));
    component.uploadForm.setValue({title: 'Titre', description: 'Description', visibility: 'MEMBERS'});

    // #when
    component.onUpload();

    // #then
    expect(documentService.upload).toHaveBeenCalledWith(
      expect.any(File), 'Titre', 'Description', 'MEMBERS');
  });

  it('sends ADMIN_ONLY as the technical visibility value on submit', () => {
    // #given
    documentService.upload.mockReturnValue(of({} as DocumentItem));
    component.selectedFile.set(new File(['contenu'], 'doc.pdf'));
    component.uploadForm.setValue({title: 'Titre', description: 'Description', visibility: 'ADMIN_ONLY'});

    // #when
    component.onUpload();

    // #then
    expect(documentService.upload).toHaveBeenCalledWith(
      expect.any(File), 'Titre', 'Description', 'ADMIN_ONLY');
  });

  it('does not submit while a request is pending, preventing double submission', () => {
    // #given
    const pending = new Subject<DocumentItem>();
    documentService.upload.mockReturnValue(pending.asObservable());
    component.selectedFile.set(new File(['contenu'], 'doc.pdf'));
    component.uploadForm.setValue({title: 'Titre', description: 'Description', visibility: 'MEMBERS'});

    // #when
    component.onUpload();
    component.onUpload();

    // #then
    expect(documentService.upload).toHaveBeenCalledTimes(1);
  });

  it('surfaces an error message and re-enables the form on failure', () => {
    // #given
    documentService.upload.mockReturnValue(throwError(() => ({status: 400})));
    component.selectedFile.set(new File(['contenu'], 'doc.pdf'));
    component.uploadForm.setValue({title: 'Titre', description: 'Description', visibility: 'MEMBERS'});

    // #when
    component.onUpload();

    // #then
    expect(component.errorMessage()).not.toBeNull();
    expect(component.uploading()).toBe(false);
  });

  // ===== Liste des documents : badge « Comité uniquement » =====

  it('displays a "Comité uniquement" badge for an ADMIN_ONLY document', async () => {
    // #given
    documentService.getAll.mockReturnValue(of([adminOnlyDoc]));

    // #when
    component.loadDocuments();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Comité uniquement');
  });

  it('does not display a badge for a MEMBERS document', async () => {
    // #given
    documentService.getAll.mockReturnValue(of([membersDoc]));

    // #when
    component.loadDocuments();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Comité uniquement');
  });
});
