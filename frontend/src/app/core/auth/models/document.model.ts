// features/members-portal/document.model.ts
export type DocumentVisibility = 'MEMBERS' | 'ADMIN_ONLY';

export interface DocumentItem {
  id: number;
  title: string;
  description: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  uploadedBy: string;
  visibility: DocumentVisibility;
  uploadedAt: string;
}
