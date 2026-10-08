# Travailler à plusieurs sur BloodBound

Ce guide est fait pour des gens qui **ne codent pas** et qui font tout passer par **Claude Code**.
Il n'y a rien à comprendre au code : il suffit de suivre les étapes, dans l'ordre.

Claude connaît les règles du projet (elles sont écrites dans `CLAUDE.md`), donc les phrases à lui dire
sont données telles quelles ci-dessous.

---

## 1. Installation (une seule fois par personne)

1. **Un compte GitHub** sur https://github.com. Le propriétaire du dépôt t'invite comme collaborateur
   (*Settings → Collaborators → Add people*) ; accepte l'invitation reçue par mail.
2. **Installer**, dans cet ordre :
   - Git : https://git-scm.com/download/win (laisser toutes les options par défaut) ;
   - Java 21 : https://adoptium.net/temurin/releases/?version=21 , choisir **JDK** et le fichier
     **.msi**, et pendant l'installation activer « Set JAVA_HOME variable » ;
   - l'application Claude : https://claude.ai/download , puis se connecter et ouvrir l'onglet
     **Code**. Il faut un abonnement Claude qui inclut Claude Code (Pro ou Max).
3. **Récupérer le projet** : ouvre Claude Code dans un dossier de ton choix (par exemple `Documents`)
   et dis-lui :

   > Clone le dépôt https://github.com/h3xpy/BloodBound-1.21.X.git ici, puis configure Git avec mon
   > pseudo GitHub `TON_PSEUDO` et mon adresse noreply GitHub.

   L'adresse noreply se trouve sur GitHub : *Settings → Emails*, cocher « Keep my email addresses
   private », elle ressemble à `12345678+pseudo@users.noreply.github.com`. Elle évite que ton vrai
   mail apparaisse publiquement dans l'historique.
4. **Ouvrir le projet** : dans Claude Code, ouvre le dossier `BloodBound-1.21.X` qui vient d'être
   créé. C'est ce dossier qu'il faudra ouvrir à chaque fois.
5. **Vérifier que tout marche** :

   > Compile le mod et lance le jeu de développement

   La première compilation télécharge Minecraft et NeoForge : compte 10 à 20 minutes, une seule fois.
   Le jeu s'ouvre sur le menu principal : crée un monde de test (créatif, c'est plus pratique) et
   dis à Claude son nom, il s'en servira ensuite pour ses vérifications.

La première fois que Claude enverra ton travail sur GitHub, une fenêtre de connexion GitHub s'ouvrira
dans ton navigateur : connecte-toi, c'est tout.

---

## 2. La règle d'or

**Personne ne travaille directement sur `main`.** `main`, c'est la version officielle du mod, celle
qui va sur le serveur. Chaque nouvelle chose (un perk, un bug, un équilibrage) se fait sur une
**branche** à part, puis arrive dans `main` par une **Pull Request** (PR).

Et avant de commencer quelque chose, **dites-vous qui fait quoi** (Discord, message…). Deux personnes
qui modifient le même perk en même temps, c'est la seule vraie source d'ennuis.

---

## 3. La procédure, à chaque fois

### a. Commencer une tâche

> Nouvelle tâche : *[décris ce que tu veux, comme d'habitude : nouveau perk, bug, nerf…]*

Claude va tout seul :
- récupérer la dernière version de `main` (le travail de l'autre) ;
- créer une branche pour ta tâche ;
- faire les changements, compiler et vérifier que le jeu se lance.

### b. Tester en jeu

Lance le jeu de développement et teste. S'il y a un problème, dis-le à Claude comme d'habitude ;
tout reste sur ta branche, `main` n'est pas touché.

### c. Envoyer ton travail

> Commit and push

Claude envoie ta branche sur GitHub et te donne un **lien pour ouvrir la Pull Request**.

### d. Ouvrir la Pull Request

1. Ouvre le lien donné par Claude (ou va sur la page du dépôt : GitHub affiche un bandeau jaune
   « Compare & pull request »).
2. Remplis la petite liste à cocher qui apparaît et clique **Create pull request**.
3. Attends la **coche verte** (le build de GitHub, 2 à 3 minutes). Une croix rouge veut dire que ça ne
   compile pas : dis à Claude « le build de ma PR a échoué » et il s'en occupe.

### e. Fusionner (merger)

Idéalement, **l'autre** récupère ta branche et teste en jeu avant :

> Récupère la branche de la PR n°*X* pour que je la teste

Quand c'est bon : sur la page de la PR, **Merge pull request** puis **Confirm merge**, et enfin
**Delete branch**.

### f. Revenir sur `main`

> La PR est mergée, remets-moi sur main à jour

Et on recommence au **a** pour la tâche suivante.

---

## 4. Si quelque chose se passe mal

- **« Conflict » / « conflit »** : vous avez modifié tous les deux le même endroit (ça arrive souvent
  avec la liste des perks ou les fichiers de langue). Dis à Claude :
  > Ma PR a des conflits, mets ma branche à jour avec main et résous-les

  Puis reteste en jeu avant de merger.
- **Tu ne sais plus où tu en es** :
  > Où j'en suis ? Sur quelle branche, et qu'est-ce qui n'est pas encore envoyé ?
- **Tu veux tout annuler sur ta branche** : demande à Claude, il te dira ce qui sera perdu avant de
  le faire.
- **Le jeu de test refuse de se lancer parce que le monde est « verrouillé »** : un autre jeu de
  développement tourne déjà. Ferme-le d'abord.

Ce qu'il ne faut **jamais** faire, même si on te le propose : forcer un push (`force push`) sur `main`,
ou supprimer `main`.

---

## 5. Sortir une version pour le serveur

Une seule personne s'en charge (celle qui gère le serveur), depuis `main` à jour :

> Passe la version à 1.x.y, compile le jar et dis-moi où il est

Le jar à mettre sur le serveur est dans `build/libs/`.

---

## 6. Réglage GitHub conseillé (une fois, par le propriétaire du dépôt)

Pour que personne ne puisse envoyer par erreur directement sur `main`. Sur la page du dépôt :
**Settings → Rules → Rulesets → New ruleset → New branch ruleset**, puis, de haut en bas :

1. **Ruleset name** : `protection-main` (le nom n'a pas d'importance).
2. **Enforcement status** : **Active**. (*Disabled* ne protège rien, *Evaluate* ne fait qu'observer.)
3. **Bypass list** : laisser vide, pour que les règles s'appliquent à tout le monde.
4. **Target branches** : cliquer **Add target → Include default branch** (ou *Include by pattern*
   et taper `main`). Une ligne doit apparaître sous « Target branches ». Sans elle, GitHub affiche
   « This ruleset does not target any resources and will not be applied » et la règle ne fait rien.
5. Dans **Rules** :
   - **Restrict deletions** : coché (déjà coché par défaut) ;
   - **Block force pushes** : coché (déjà coché par défaut) ;
   - **Require a pull request before merging** : coché, et laisser **Required approvals** à **0** ;
   - **Require status checks to pass** : coché, puis cliquer **+ Add checks** juste en dessous,
     taper `build` et cliquer la ligne **build** (petite icône GitHub Actions). Elle doit apparaître
     sous la case. GitHub ne propose que les vérifications lancées ces 7 derniers jours : si elle
     n'apparaît pas, pousser n'importe quelle branche pour relancer le build, puis réessayer.
6. Tout en bas : **Create**.

Après ça, la seule manière de changer `main`, c'est une Pull Request dont le build est vert : le
bouton **Merge** reste grisé tant que la coche verte n'est pas là.

---

## 7. Les images (textures, icônes)

Les icônes de perks vont dans `src/main/resources/assets/bloodbound/textures/gui/sprites/perk/`
(`nom_du_perk_tier1.png`, `_tier2`, `_tier3`), celles d'addons dans `.../sprites/addon/nom.png`. Pour
un modèle Blockbench, dépose l'export n'importe où dans le projet et dis à Claude ce que c'est : il le
convertit.
