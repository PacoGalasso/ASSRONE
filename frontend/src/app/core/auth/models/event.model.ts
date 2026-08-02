export type EventType = 'FORMATION' | 'WEBINAIRE' | 'ATELIER';

export interface EventItem {
  id: number;
  title: string;
  description: string;
  type: EventType;
  eventDate: string;       // ISO yyyy-MM-dd
  startTime: string;       // HH:mm:ss
  endTime: string;
  location: string;
  maxParticipants: number;
  currentParticipants: number;
}
export interface CreateEventRequest {
  title: string;
  description: string;
  type: EventType;
  eventDate: string;
  startTime: string;
  endTime: string;
  location: string;
  maxParticipants: number;
}
