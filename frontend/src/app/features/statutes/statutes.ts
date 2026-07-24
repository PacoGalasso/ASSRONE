// features/statutes/statutes.ts
import {Component, inject} from '@angular/core';
import {DOCUMENT} from '@angular/common';

interface Article {
  number: number;
  text: string;
  items?: string[];
  outro?: string;
}

interface StatuteSection {
  title: string;
  articles: Article[];
}

@Component({
  selector: 'app-statutes',
  standalone: true,
  templateUrl: './statutes.html',
})
export class Statutes {
  protected readonly sections: StatuteSection[] = [
    {
      title: 'Forme juridique, but et siège',
      articles: [
        {
          number: 1,
          text: "Sous le nom de « Association suisse-romande de neuropédagogie » (ci-dessous L'Association) est créé une association à but non lucratif régie par les présents statuts et par les articles 60 et suivants du Code civil suisse.",
        },
        {
          number: 2,
          text: "L'Association a pour but :",
          items: [
            "D'établir et maintenir des liens professionnels et d'échanger entre les personnes formées en neuropédagogie et toute personne intéressée.",
            "De favoriser les échanges entre les chercheurs et chercheuses, le corps enseignant, et les praticiens et praticiennes.",
            "D'établir des partenariats avec des écoles, des universités et des centres de formation en Suisse et à l'étranger.",
            "D'œuvrer à la reconnaissance et à l'identité de la profession de neuropédagogue.",
            "De promouvoir le domaine de la neuropédagogie (formations, conférences, ateliers, méthodes et approches).",
            "D'organiser des campagnes d'information pour faire connaître les bienfaits de la neuropédagogie auprès des parents, entreprises, institutions, écoles et organes décideurs.",
            "De développer les connaissances et diffuser les informations dans le domaine de la neuropédagogie.",
          ],
          outro: "L'Association est neutre tant sur le plan politique que confessionnel.",
        },
        {
          number: 3,
          text: "Le siège de l'Association est en Suisse Romande. Sa durée est illimitée.",
        },
      ],
    },
    {
      title: 'Organisation',
      articles: [
        {
          number: 4,
          text: "Les organes de l'Association sont :",
          items: ["L'Assemblée générale.", "Le Comité.", "Les vérificateurs ou vérificatrices des comptes."],
        },
        {
          number: 5,
          text: "Les ressources de l'Association sont constituées :",
          items: [
            "Par les cotisations annuelles de ses membres.",
            "Par des dons, legs ou autres libéralités qui pourront être faits à l'Association.",
            "Par des produits des activités de l'Association.",
          ],
          outro: "L'exercice social commence le 1er janvier et se termine le 31 décembre de chaque année. Ses engagements sont garantis par ses biens, à l'exclusion de toute responsabilité personnelle de ses membres.",
        },
      ],
    },
    {
      title: 'Membres',
      articles: [
        {
          number: 6,
          text: "Peuvent être membres toutes les personnes ou les organismes intéressés par les activités et la réalisation des objectifs fixés par l'art. 2. L'adhésion se fait par paiement de la cotisation annuelle.",
        },
        {
          number: 7,
          text: "L'Association est composée de :",
          items: [
            "Membres individuel-les actifs ou actives (personnes physiques impliquées dans la neuropédagogie).",
            "Membres individuel-le-s de soutien (personnes physiques soutenant les activités de l'Association).",
            "Membres collectifs actifs (personnes morales impliquées dans la neuropédagogie).",
            "Membres collectifs de soutien (personnes morales soutenant les activités de l'Association).",
          ],
        },
        {
          number: 8,
          text: "Les demandes d'admission sont adressées au Comité. Le Comité décide librement de l'admission des nouveaux membres sans obligation de justification et en informe l'Assemblée générale.",
        },
        {
          number: 9,
          text: "L'Association se dote d'une charte, adoptée par l'Assemblée générale, qui précise ses valeurs, ses principes d'action et son engagement éthique. Cette charte complète les présents statuts et guide les activités de l'Association. Lors de l'adhésion, les membres actifs s'engagent à signer et respecter la charte de L'Association.",
        },
        {
          number: 10,
          text: "La qualité de membre se perd par la démission, par l'exclusion ou par le décès.",
          items: [
            "La démission se fait par courrier écrit ou électronique adressé au Président ou à la Présidente de l'Association. Elle prend effet au 1er janvier qui suit la réception de la lettre de démission. Dans tous les cas la cotisation de l'année en cours reste due.",
            "L'exclusion est du ressort du Comité. Elle est prononcée sans indication de motifs. La personne concernée peut recourir contre cette décision devant l'Assemblée générale, dans un délai d'un mois dès réception de la décision. Le ou la membre dont l'exclusion est envisagée a le droit d'être entendu-e au préalable par le Comité et par l'Assemblée générale. L'Assemblée générale statue lors de sa prochaine séance (également sans obligation de justification).",
            "Le non-paiement répété des cotisations entraine également l'exclusion.",
            "Le défaut de paiement de la cotisation après deux rappels est assimilé à une démission.",
          ],
        },
      ],
    },
    {
      title: 'Assemblée générale',
      articles: [
        {
          number: 11,
          text: "L'Assemblée générale est le pouvoir suprême de l'Association. Elle comprend tous les membres de celle-ci.",
        },
        {
          number: 12,
          text: "En particulier, elle est compétente pour :",
          items: [
            "Définir les actions de l'Association et traiter des questions de portée générale.",
            "Adopter l'ordre du jour de l'Assemblée et approuver le procès-verbal de la dernière Assemblée.",
            "Approuver les rapports du Comité et des vérificateurs ou vérificatrices des comptes.",
            "Approuver les comptes, le bilan et le budget.",
            "Donner décharge au Comité pour son activité.",
            "Fixer la cotisation annuelle.",
            "Élire la Présidente ou le Président, le Comité, ainsi que les vérificateurs ou vérificatrices des comptes.",
            "Adopter et modifier les statuts.",
            "Dissoudre l'Association.",
            "Entendre et traiter les recours d'exclusion.",
            "Prendre position sur les autres projets portés à l'ordre du jour.",
          ],
          outro: "L'Assemblée générale peut saisir ou être saisie de tout objet qu'elle n'a pas confié à un autre organe.",
        },
        {
          number: 13,
          text: "L'Assemblée générale se réunit au moins une fois par an sur convocation du Comité. Le Comité peut convoquer des assemblées générales extraordinaires aussi souvent que le besoin s'en fait sentir. L'Assemblée générale extraordinaire se réunit également à la demande d'au moins un cinquième des membres de l'Association.",
        },
        {
          number: 14,
          text: "Les assemblées sont convoquées au moins 20 jours calendaires à l'avance par le Comité. La convocation est adressée par courrier écrit ou électronique et comprend l'ordre du jour de l'Assemblée. Si le Comité le juge nécessaire, l'Assemblée peut également être tenue par des moyens électroniques.",
        },
        {
          number: 15,
          text: "Le Comité est tenu de porter à l'ordre du jour de l'Assemblée générale (ordinaire ou extraordinaire) toute proposition d'un membre ou d'une membre, présentée par courrier postal ou électronique au moins 10 jours calendaires à l'avance.",
        },
        {
          number: 16,
          text: "L'assemblée est présidée par la Présidente ou le Président de l'Association ou par une autre personne membre proposé par le Comité. Le ou la secrétaire ou tout autre membre du comité tient le procès-verbal et le transmet dans un délai de 15 jours à l'ensemble des membres.",
        },
        {
          number: 17,
          text: "Les décisions de l'Assemblée générale sont prises à la majorité simple des voix exprimées, sans tenir compte des abstentions et d'éventuels bulletins nuls. Les membres collectifs actifs ou de soutien comptent pour une voix. En cas d'égalité des voix, celle du Président ou de la Présidente de l'Assemblée compte double. En outre, une proposition à laquelle tous les membres adhèrent par correspondance ou par consultation équivaut à une décision de l'Assemblée générale. Les décisions relatives à la modification des statuts ne peuvent être prises qu'à la majorité des 2/3 des membres présent-e-s. Les modifications proposées doivent être adressées aux membres par écrit, en même temps que la convocation à l'Assemblée générale.",
        },
        {
          number: 18,
          text: "Les votations ont lieu à main levée ou au scrutin secret, selon les informations recueillies par le Comité lors du retour des bulletins de confirmation de participation à l'Assemblée générale. À la demande de 5 membres au moins, elles auront lieu au scrutin secret. Il n'y a pas de vote par procuration.",
        },
      ],
    },
    {
      title: 'Comité',
      articles: [
        {
          number: 19,
          text: "Le Comité exécute et applique les décisions de l'Assemblée générale. Il conduit l'Association et prend toutes les mesures utiles pour que les buts fixés soient atteints. Le Comité statue sur tous les points qui ne sont pas expressément réservés à l'Assemblée générale.",
        },
        {
          number: 20,
          text: "Le Comité se compose de quatre à onze membres, dont un Président ou une Présidente, un Vice-président ou une Vice-Présidente, un-e secrétaire et un trésorier ou trésorière nommé-e-s pour deux ans par l'Assemblée générale et rééligibles. L'élection se fait à la majorité absolue au premier tour, à la majorité simple au second tour. Le scrutin est secret si un ou une membre de l'Assemblée générale le demande. Les fonctions sont réalisées à titre gracieux.",
        },
        {
          number: 21,
          text: "Le Comité gère les affaires de l'Association. Il est notamment chargé de :",
          items: [
            "Convoquer les Assemblées générales ordinaires et extraordinaires.",
            "Prendre les décisions relatives à l'admission et l'éventuelle exclusion de membres.",
            "Être garant du respect de la charte.",
            "Veiller à l'observation des statuts et à l'exécution des décisions de l'Assemblée générale.",
            "Organiser des réunions et des actions d'intérêt général pour les membres.",
            "Procurer autant que possible, à l'Association les moyens de ses actions.",
            "Régler les affaires courantes.",
            "Soumettre à l'Assemblée générale un rapport d'activité, des comptes annuels révisés par les vérificateurs ou vérificatrices, et un budget.",
          ],
          outro: "Il se réunit aussi souvent que les affaires de l'Association l'exigent et au minimum 3 fois par année. Si nécessaire, il peut tenir ses réunions à distance par voie électronique. À défaut d'un consentement, les décisions sont prises à la majorité absolue des membres présent-e-s. Concernant les postes de Président ou Présidente, de Vice-Président ou Vice-présidente, de secrétaire et de trésorier ou trésorière, en cas de vacances, de maladie, de démission ou pour tout autre motif d'absence nécessitant un remplacement en cours de mandat, le Comité se complète par cooptation jusqu'à la prochaine Assemblée Générale. Les membres du Comité de l'Association travaillent de manière bénévole, sous réserve du remboursement de leurs frais effectifs qui font l'objet d'une justification écrite et sont validés par le Comité.",
        },
        {
          number: 22,
          text: "L'Association est valablement engagée par la signature collective à deux du Président ou de la Présidente et d'une des personnes membre du Comité. En l'absence du Président ou de la Présidente, une autre personne membre du comité le ou la remplace.",
        },
      ],
    },
    {
      title: 'Vérificateurs ou vérificatrices des comptes',
      articles: [
        {
          number: 23,
          text: "Les vérificateurs ou vérificatrices des comptes vérifient la gestion financière de l'Association et présentent un rapport à l'Assemblée générale. Ils ou elles sont désigné-e-s par l'Assemblée générale, en dehors des membres du comité.",
        },
      ],
    },
    {
      title: 'Dissolution',
      articles: [
        {
          number: 24,
          text: "La dissolution de l'Association est décidée par l'Assemblée générale à la majorité des deux tiers des membres présent-e-s. Elle doit être mentionnée dans la convocation à cette assemblée. L'actif éventuel sera attribué à un organisme se proposant d'atteindre des buts analogues.",
        },
      ],
    },
  ];
  private document = inject(DOCUMENT);

  scrollToSection(index: number, event: Event): void {
    event.preventDefault();
    const element = this.document.getElementById('section-' + index);
    element?.scrollIntoView({behavior: 'smooth', block: 'start'});
  }
}
