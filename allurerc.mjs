// Configuration Allure 3 du rapport de contrôle de fumée et des tests de latence (lue par le plugin Jenkins Allure
// dans le workspace). Un environnement par navigateur : sélecteur en haut du rapport, et arbre regroupé par jeu de
// tests et navigateur (« Fumée chrome », « Latence chrome »…) puis par page.
// Pas d'import : le fichier doit fonctionner avec l'Allure embarqué par Jenkins, sans node_modules.

const navigateur = (nom) => ({ labels }) =>
  labels.find((label) => label.name === "parentSuite" && label.value.endsWith(` ${nom}`));

export default {
  name: "socle-testapp — fumée et latence",
  plugins: {
    awesome: {
      options: {
        groupBy: ["parentSuite", "suite", "subSuite"],
      },
    },
  },
  environments: {
    chrome: { matcher: navigateur("chrome") },
    firefox: { matcher: navigateur("firefox") },
    edge: { matcher: navigateur("edge") },
  },
};
