// features/membership/membership.ts
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DOCUMENT } from '@angular/common';

@Component({
  selector: 'app-membership',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './membership.html',
})
export class Membership {
  private document = inject(DOCUMENT);

  protected readonly benefits = [
    {
      title: 'Partager et apprendre',
      description: "Accédez à un espace d'échanges professionnels, de réflexion et de connaissances actualisées.",
    },
    {
      title: "S'engager et contribuer",
      description: 'Participez à des projets, groupes de travail et actions de sensibilisation.',
    },
    {
      title: 'Évoluer ensemble',
      description: 'Développez vos pratiques grâce à des informations et à un réseau solidaire.',
    },
  ];

  protected readonly plans = [
    { title: 'Membre individuel·le', price: '50', description: 'Personnes physiques impliquées dans la neuropédagogie' },
    { title: 'Membre collectif', price: '150', description: 'Personnes morales impliquées dans la neuropédagogie' },
  ];

  protected readonly steps = [
    {
      title: 'Acceptez la charte',
      description: "Un engagement éthique partagé. Découvrez les valeurs, principes éthiques et engagements qui guident l'association.",
      link: { label: 'Découvrir la charte', route: '/charter' },
    },
    {
      title: 'Remplissez le formulaire',
      description: "Une fois la charte acceptée, vous serez redirigé vers le formulaire d'adhésion — quelques minutes suffisent.",
      link: null,
    },
    {
      title: 'Recevez votre confirmation',
      description: 'Votre adhésion est confirmée : bienvenue dans la communauté ASSRONE !',
      link: null,
    },
    {
      title: 'Effectuez le paiement',
      description: 'Validez votre adhésion par le règlement de la cotisation annuelle et recevez vos identifiants ASSRONE par courriel.',
      link: null,
    },
  ];

  scrollToId(id: string, event: Event): void {
    event.preventDefault();
    this.document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
