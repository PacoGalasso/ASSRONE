// features/about/about.ts
import {Component} from '@angular/core';

@Component({
  selector: 'app-about',
  standalone: true,
  templateUrl: './about.html',
})
export class About {
  protected readonly historyParagraphs = [
    "ASSRONE est née d'un constat partagé : le besoin de créer des liens entre les professionnel·le·s de la neuropédagogie, la recherche, le monde éducatif et la pratique de terrain.",
    "Face aux enjeux liés aux troubles du neurodéveloppement et à la neurodiversité, l'association s'est structurée pour favoriser le partage de savoirs, soutenir les pratiques et œuvrer à la reconnaissance de la spécialisation en neuropédagogie en Suisse.",
    "Aujourd'hui, ASSRONE développe un réseau actif, engagé et collaboratif, au service des enfants, des adultes et des professionnel·le·s.",
  ];

  protected readonly values = [
    {
      title: 'Bienveillance',
      description: "Écoute, respect et non-jugement guident nos échanges. Nous favorisons un climat de confiance, soutenant et sécurisant."
    },
    {
      title: 'Respect des singularités',
      description: "Chaque personne est accueillie dans sa diversité, avec ses besoins, son rythme et ses particularités neurocognitives."
    },
    {
      title: 'Collaboration',
      description: "Nous croyons à la force du collectif : échanges, co-construction et partenariats enrichissent les pratiques et les savoirs."
    },
    {
      title: 'Conscience professionnelle',
      description: "Nos actions s'appuient sur des connaissances scientifiques validées, une réflexion critique et une éthique professionnelle exigeante."
    },
  ];

  protected readonly president = {
    name: 'Béatrice Amoroso Galasso',
    role: 'Présidente',
    initials: 'BA',
  };

  protected readonly team = [
    {name: 'Helena Da Silva', role: 'Vice-présidente', initials: 'HD'},
    {name: 'Candice Floch', role: 'Co-secrétaire', initials: 'CF'},
    {name: 'Amélie Gautier Meynier', role: 'Co-secrétaire', initials: 'AG'},
    {name: 'Sandra Schaller', role: 'Trésorière', initials: 'SS'},
    {name: 'Isabelle Santarelli', role: 'Membre du Comité', initials: 'IS'},
    {name: 'Poste à pourvoir', role: 'Membre du Comité', initials: '—'},
  ];

  protected readonly commitments = [
    'Diffuser des connaissances fiables et accessibles en neuropédagogie',
    'Soutenir les pratiques professionnelles par des ressources, formations et échanges',
    'Sensibiliser la société aux enjeux de la neurodiversité',
    'Contribuer à la reconnaissance du métier de neuropédagogue',
    'Travailler en réseau avec les acteurs éducatifs, institutionnels et scientifiques',
  ];
}
