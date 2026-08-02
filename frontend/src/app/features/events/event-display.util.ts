import {EventItem} from '../../core/auth/models/event.model';

const BADGE_CLASSES: Record<string, string> = {
  FORMATION: 'bg-purple-100 text-purple-700',
  WEBINAIRE: 'bg-pink-100 text-pink-700',
  ATELIER: 'bg-green-100 text-green-700',
};

const TYPE_LABELS: Record<string, string> = {
  FORMATION: 'Formation',
  WEBINAIRE: 'Webinaire',
  ATELIER: 'Atelier',
};

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

export function badgeClass(type: string): string {
  return BADGE_CLASSES[type] ?? 'bg-gray-100 text-gray-700';
}

export function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

export function eventDay(iso: string): string {
  return iso.split('-')[2];
}

export function eventMonth(iso: string): string {
  return MONTHS[parseInt(iso.split('-')[1], 10) - 1];
}

export function fillPercent(event: EventItem): number {
  if (!event.maxParticipants) return 0;
  return Math.min(100, Math.round((event.currentParticipants / event.maxParticipants) * 100));
}
