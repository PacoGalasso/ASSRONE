import { Component, input } from '@angular/core';

@Component({
  selector: 'app-section-heading',
  standalone: true,
  templateUrl: './section-heading.html',
})
export class SectionHeading {
  eyebrow = input.required<string>();
  title = input.required<string>();
  subtitle = input<string>();
  align = input<'left' | 'center'>('left');
}
