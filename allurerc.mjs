// Configuration Allure 3 du rapport de contrôle de fumée (lue par le plugin Jenkins Allure dans le workspace).
// Un environnement par navigateur : sélecteur en haut du rapport, et arbre regroupé par navigateur puis par page.
// Pas d'import : le fichier doit fonctionner avec l'Allure embarqué par Jenkins, sans node_modules.

const navigateur = (nom) => ({ labels }) =>
  labels.find((label) => label.name === "parentSuite" && label.value === `Fumée ${nom}`);

export default {
  name: "socle-testapp — contrôle de fumée",
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
