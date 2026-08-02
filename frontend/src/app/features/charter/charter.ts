// features/charter/charter.ts
import {Component, inject} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {CharterAcceptanceService} from '../../core/auth/services/charter-acceptance-service';

@Component({
  selector: 'app-charter',
  standalone: true,
  imports: [
    RouterLink
  ],
  templateUrl: './charter.html',
})
export class Charter {
  protected readonly preamble = "L'Association Suisse Romande de Neuropédagogie (ASSRONE) réunit des professionnel·le·s d'horizons différents, toutes et tous formé-e-s à la neuropédagogie, une approche spécialisée visant l'accompagnement des enfants et adultes ayant un TDN (TSA, TDA-H, troubles spécifiques des apprentissages) prenant également en compte les fonctionnements liés aux différents profils sensoriels et au haut potentiel. Ensemble, nous souhaitons rendre accessibles, au plus grand nombre, les connaissances spécifiques à ce domaine, favoriser le partage entre professionnel-le-s, apporter du soutien à la pratique et encourager une pédagogie inclusive, bienveillante et respectueuse des singularités.";
  protected readonly goals = [
    {
      title: 'Partager',
      description: 'Échanger les savoirs en neuropédagogie et toutes disciplines liées, créer des liens et partenariats.'
    },
    {
      title: 'Transmettre',
      description: 'Diffuser largement les connaissances spécifiques, mais aussi accompagner dans les pratiques par des outils, ressources, formations et appuis concrets.'
    },
    {
      title: 'Sensibiliser',
      description: "Mettre en évidence les difficultés et facilités des neuroatypies, leurs inconvénients et avantages, mais aussi éclairer sur les enjeux de la neurodiversité."
    },
  ];
  protected readonly values = [
    {
      title: 'Bienveillance',
      description: "Adopter une attitude d'écoute active et de non-jugement dans les différents échanges. Favoriser un climat de confiance et soutenant propice au développement personnel et cognitif."
    },
    {title: 'Respect des singularités', description: 'Accueillir les besoins, la singularité et le rythme de chacun.'},
    {
      title: 'Collaboration',
      description: "Favoriser les échanges, co-construire des projets et coopérer avec les membres et partenaires de l'association afin de transmettre et diffuser des savoirs pour enrichir la pratique de chacun·e."
    },
    {
      title: 'Conscience professionnelle',
      description: "S'appuyer sur des connaissances scientifiques validées et actualisées de la recherche afin de réfléchir de manière critique à nos pratiques pédagogiques. Cultiver un esprit professionnel et des relations collégiales empreintes de sincérité, de solidarité, de respect et de confiance mutuelle."
    },
  ];
  protected readonly commitmentIntro = "Le Comité, les membres et les partenaires externes d'ASSRONE s'engagent, dans un idéal de solidarité et de collaboration, à travailler en réseau et en partenariat, œuvrer dans un esprit constructif et assurer la pérennité éthique d'ASSRONE.";
  protected readonly actorGroups = [
    {
      title: 'Le Comité vis-à-vis des membres',
      items: ["Exerce ses fonctions avec intégrité, transparence et responsabilité."],
    },
    {
      title: 'Sensibilisation',
      items: [
        'Rendre accessible à tous les connaissances actuelles en neuropédagogie.',
        "Mener et soutenir des actions de vulgarisation et d'information.",
      ],
    },
    {
      title: "Les membres d'ASSRONE",
      items: [
        "Représentent tous les adhérents et défendent les objectifs d'ASSRONE avec impartialité.",
        "Portent les valeurs fondamentales de l'association dans le respect et la bienveillance.",
        'Veillent à préserver la confidentialité des informations partagées.',
        "S'informent et développent les connaissances dans le domaine de la neuropédagogie.",
        'Diffusent les informations actualisées dans le domaine de la neuropédagogie.',
        'Appliquent les statuts et la charte.',
      ],
    },
    {
      title: 'Formation et recherche',
      items: [
        'Promouvoir et proposer des formations continues.',
        'Intervenir auprès du grand public, des institutions et des milieux éducatifs et professionnels.',
        'Mettre à disposition des données, savoirs et ressources au service de la recherche.',
        "Coopérer et travailler en réseau en faveur d'une compréhension des différents fonctionnements.",
      ],
    },
    {
      title: 'ASSRONE vis-à-vis des partenaires externes',
      items: ['Travaille en réseau.', 'Construit des partenariats les plus ouverts possibles.'],
    },
    {
      title: 'Reconnaissance de la profession',
      items: [
        "Participer activement à la création, à la structuration et à la reconnaissance du métier de neuropédagogue en Suisse, en s'impliquant et en créant des groupes de travail.",
        'Interpeler les décideurs.',
      ],
    },
  ];
  protected readonly generalDuties = [
    {
      title: 'Responsabilité',
      description: "Les membres du comité sont solidairement responsables de la bonne conduite de l'association, de faire respecter les statuts, les décisions de l'assemblée générale et de tenir les comptes."
    },
    {
      title: 'Respecter les statuts et la charte',
      description: "Les statuts et la charte de l'association définissent les règles de fonctionnement et les obligations des membres. Leur non-respect peut entraîner des sanctions, voire l'exclusion."
    },
    {
      title: 'Payer les cotisations',
      description: "Les membres sont tenus de s'acquitter de la cotisation annuelle prévue par l'association."
    },
    {
      title: 'Loyauté et confidentialité',
      description: "Les membres doivent défendre les intérêts de l'association et agir de bonne foi envers elle et ses autres membres. La confidentialité repose sur le respect des données personnelles, conformément au RGPD et aux lois nationales : recueillir uniquement les données nécessaires, limiter l'accès aux personnes autorisées, obtenir le consentement pour la diffusion d'informations, informer sur l'utilisation des données et s'assurer que seules les personnes ayant un intérêt légitime y ont accès."
    },
    {
      title: 'Participer aux assemblées générales',
      description: "C'est un devoir important pour la vie démocratique de l'association. Les membres peuvent être impliqués dans les projets et actions selon leur niveau d'adhésion."
    },
    {
      title: 'Prise en charge de fonctions',
      description: "Les membres peuvent accepter de prendre en charge bénévolement des rôles ou fonctions spécifiques au sein de l'association, comme précisé dans les statuts."
    },
  ];
  protected readonly consequences = [
    {
      title: 'Sanctions internes',
      description: "Le non-respect des obligations peut mener à des sanctions définies dans les statuts, allant de l'avertissement à l'exclusion du membre ou même des poursuites pénales."
    },
    {
      title: 'Responsabilités civiles et pénales',
      description: "Un membre peut être tenu responsable des dommages qu'il cause à l'association, à d'autres membres ou à des tiers, que ce soit par faute intentionnelle ou négligence. Les lois fédérales et cantonales prévalent sur les principes et recommandations du présent document."
    },
  ];
  protected readonly legalReferences = [
    'Constitution Fédérale',
    'Code Pénal Suisse',
    "Loi d'application du droit fédéral de la protection de l'adulte et de l'enfant",
    'Loi sur la protection des données',
    'Loi sur le travail',
  ];
  protected readonly ethicalReferences = [
    "Déclaration universelle des droits de l'homme",
    "Convention sur l'élimination de toutes les formes de discrimination raciale",
    "Convention des droits de l'enfant",
    'Convention sur les droits des personnes handicapées',
    'Code de déontologie du travail social en Suisse',
  ];
  private charterAcceptanceService = inject(CharterAcceptanceService);
  private router = inject(Router);

  acceptCharter(): void {
    this.charterAcceptanceService.accept();
    this.router.navigate(['/membership/apply']);
  }
}
