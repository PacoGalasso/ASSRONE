// features/about/committee-member.model.ts
export interface CommitteeMemberDto {
  id: number;
  firstName: string;
  lastName: string;
  role: string;
  description: string | null;
  hasPhoto: boolean;
  displayOrder: number;
  active: boolean;
}

export interface CommitteeMemberFormValue {
  firstName: string;
  lastName: string;
  role: string;
  description: string;
  displayOrder: number;
  active: boolean;
}
