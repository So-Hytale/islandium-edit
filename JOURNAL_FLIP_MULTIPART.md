# Journal des modifications - Flip Multipart

## Contexte
Bug: les blocs multipart (bancs, lits, lanternes) sont deplacees/decales apres copy + flip + paste.
Fichier principal: `src/main/java/com/islandium/edit/operation/ClipboardOperations.java`

---

## v1 - Yaw swap only (2026-02-11 ~08:30)
**Approche**: Pour les blocs multipart, ajouter un swap 0<->2 pour flipX et 1<->3 pour flipZ, EN PLUS du swap standard (1<->3 pour flipX, 0<->2 pour flipZ).
**Resultat**: Bancs et lanternes OK. Lits mal places, un banc manquant.
**Analyse**: Le swap standard 1<->3 sur les bancs (yaw=1) change la direction Z du filler de +Z a -Z, causant un deplacement. Les bancs etant SYMMETRIC, ce swap est inutile visuellement mais casse la position du filler.

## v2 - Multipart-only swap (2026-02-11 ~09:00)
**Approche**: Pour les multipart, SEULEMENT le swap d'axe correspondant au flip (0<->2 pour flipX, 1<->3 pour flipZ). Pas de swap standard pour les multipart.
**Resultat**: Bancs OK. Lits "pas dans le bon sens" selon l'utilisateur.
**Analyse**: Lit a yaw=1 reste yaw=1 (pas de 1<->3 swap). L'utilisateur s'attendait a voir le lit changer d'orientation, mais logiquement un lit orientee en Z ne devrait PAS changer lors d'un miroir X. Le probleme etait peut-etre une confusion visuelle ou un autre bloc.

## v3 - Standard swap + compensation position (2026-02-11 ~09:04)
**Approche**: Swap standard (1<->3 pour flipX) pour TOUS les blocs + compensation de position en Z pour les multipart dont le filler Z est inverse.
**Resultat**: Lits a rot=2 ne changent pas (le standard flipX ne touche pas 0/2). Undo laisse des artefacts.
**Analyse**: Il manquait le swap 0<->2 pour les multipart. Le swap standard seul ne suffit pas pour les blocs orientees en X.

## v4 - Double swap + compensation (2026-02-11 ~09:10)
**Approche**: Standard (1<->3) + multipart additionnel (0<->2) pour flipX. Compensation position Z pour les cas 1->3.
**Resultat**: "pire" - deplacement massif de tous les blocs multipart.
**Analyse**: Le double swap (standard + multipart) appliquee sequentiellement causait des swaps en cascade. De plus, la compensation Z creait des offsets incorrects. Bug additionnel decouvert: `isMultiPart(String)` utilisait seulement `protrudesUnitBox()` alors que `getBlockSize()` verifie aussi les dimensions > 1.0, ce qui fait que certains blocs multipart n'etaient PAS detectes comme multipart dans `transformRotation()`.

## v5 - Approche propre: multipart = swap axe uniquement (2026-02-11 10:23)
**Approche**:
- Blocs multipart + flipX: SEULEMENT swap 0<->2 (axe X du filler inverse par le miroir X)
- Blocs multipart + flipZ: SEULEMENT swap 1<->3 (axe Z du filler inverse par le miroir Z)
- Blocs normaux: swap standard inchange (1<->3 pour flipX, 0<->2 pour flipZ) + overrides
- AUCUNE compensation de position pour flipX/flipZ (seulement vFlip conserve)
- Detection multipart via `getBlockSize()` au lieu de `isMultiPart()` (plus fiable)

**Correctifs cles**:
1. `isMultiPart` detection: utilise maintenant `getBlockSize(blockType, rotationIndex)` qui verifie `protrudes || width > 1.0 || height > 1.0 || depth > 1.0`
2. Le chemin multipart est AVANT les chemins nativeFlip/overrides (priorite absolue)
3. Suppression de toute compensation position pour flipX/flipZ

**Logique**:
- FlipX mirror les coordonnees X -> seuls les fillers en X (yaw 0/2) changent de direction -> swap 0<->2
- Les fillers en Z (yaw 1/3) ne sont pas affectes par un miroir X -> pas de swap 1<->3
- Le miroir de coordonnees gere naturellement le decalage spatial du filler

**Resultat**: Bancs OK, positionnement OK. Mais les lits ont le visuel inverse (tete/pieds) car le swap 0<->2 change aussi l'apparence visuelle du lit (marque SYMMETRIC par l'API mais visuellement asymetrique).

## v6 - Aucun swap yaw + compensation position (2026-02-11 10:32)
**Approche**: NE PAS modifier le yaw des blocs multipart du tout. A la place, compenser la position de l'origin pour que le filler auto-cree par Hytale occupe la bonne zone.

**Principe**:
- Le yaw est preserve -> le visuel du bloc est identique (tete/pieds du lit, orientation du banc)
- Apres le miroir, l'origin est au mauvais bout du bloc -> on decale l'origin
- Le filler s'etend depuis l'origin dans la meme direction (inchangee)
- Le resultat : origin+filler occupent les positions correctes en miroir

**Compensation par cas** (direction filler par yaw: 0=+X, 1=+Z, 2=-X, 3=-Z):
- FlipX + yaw 0 (filler +X) : `worldX -= (gridWidth - 1)`
- FlipX + yaw 2 (filler -X) : `worldX += (gridWidth - 1)`
- FlipX + yaw 1 ou 3 : pas de compensation (filler en Z, pas affecte par miroir X)
- FlipZ + yaw 1 (filler +Z) : `worldZ -= (gridDepth - 1)`
- FlipZ + yaw 3 (filler -Z) : `worldZ += (gridDepth - 1)`
- FlipZ + yaw 0 ou 2 : pas de compensation (filler en X, pas affecte par miroir Z)
- vFlip : `worldY += (gridHeight - 1)` (filler toujours en +Y)

**Avantage par rapport a v5**: Fonctionne pour TOUS les multipart, y compris les blocs visuellement asymetriques (lit) marques SYMMETRIC par l'API. Pas besoin d'overrides speciaux.

**Resultat**: ECHEC - la compensation de position decale TOUS les blocs multipart (bancs inclus). Les bancs symetriques n'ont pas besoin de compensation, seulement les lits asymetriques. Mais on ne peut pas les distinguer via l'API (tous SYMMETRIC). La compensation est incorrecte pour les bancs car elle decale l'origin alors que le banc est visuellement identique dans les 2 sens.

## v7 - Retour a v5: swap axe uniquement, aucune compensation (2026-02-11 10:42)
**Approche**: Retour a l'approche v5 qui fonctionnait pour bancs, lanternes et la plupart des blocs:
- Blocs multipart + flipX: SEULEMENT swap 0<->2
- Blocs multipart + flipZ: SEULEMENT swap 1<->3
- AUCUNE compensation de position pour flipX/flipZ
- Detection multipart via `getBlockSize()` (correction du bug de detection)

**Raisonnement du retour**: La v5 etait la meilleure version testee. Le seul probleme signale etait le lit "pas pareil" — mais en realite, un lit au yaw 0 (filler +X) devenant yaw 2 (filler -X) apres flipX EST un miroir correct : dans un vrai miroir, la tete et les pieds du lit SONT inverses. C'est le comportement attendu d'un miroir.

La v6 essayait de "corriger" le lit en ne changeant pas le yaw et en compensant la position, mais cela cassait les bancs et tous les autres multipart symetriques.

**Conclusion**: Le swap d'axe (v5/v7) est l'approche correcte pour les multipart. Le visuel "inverse" du lit est le comportement normal d'un miroir.

**Resultat**: PARTIEL - Bancs OK pour flipX et rot180. Mais pour flipZ, 2 bancs manquants. Lits decales pour flipX et flipZ. Rot180 = seulement lit decale, bancs OK.

**Analyse post-test**: Le swap 0<->2 (flipX) ou 1<->3 (flipZ) change la direction du filler width ET depth. Pour les blocs avec depth=1 (banc 2x1x1), pas de probleme. Pour les blocs avec depth>1 (lit 2x2x3), le swap inverse aussi la direction transversale (Z pour flipX, X pour flipZ), ce qui decale l'origin.

## v8 - Compensation profondeur transversale pour grands multipart (2026-02-11 ~11:00)
**Approche**: Conserver le swap axe de v7 (0<->2 pour flipX, 1<->3 pour flipZ) + ajouter une compensation de position TRANSVERSALE uniquement pour les blocs avec depth > 1.

**Principe cle**: Quand on swap yaw 0<->2 (flipX), le filler change de direction sur l'axe X (width) mais AUSSI sur l'axe Z (depth). Pour un bloc avec depth=1 (banc), ca n'a pas d'effet. Pour un bloc avec depth=3 (lit), le filler Z change de direction → besoin de compenser Z.

**Compensation par cas**:
- FlipX + swap 2->0 : `worldZ -= (gridDepth - 1)` (profondeur passe de -Z a +Z)
- FlipX + swap 0->2 : `worldZ += (gridDepth - 1)` (profondeur passe de +Z a -Z)
- FlipZ + swap 1->3 : `worldX -= (gridDepth - 1)` (profondeur passe de -X a +X)
- FlipZ + swap 3->1 : `worldX += (gridDepth - 1)` (profondeur passe de +X a -X)
- Benches (depth=1) : compensation = 0, pas affectes
- vFlip : `worldY += (gridHeight - 1)` (inchange)

**Implementation**: La compensation utilise `origYaw` et `transYaw` pour determiner la direction du swap, et `transSizeInfo.gridDepth()` pour la taille de la compensation.

**Resultat**: ECHEC PARTIEL - flipX et rot180 OK ! Mais flipZ casse les BANCS (decales de 1 en X).

**Analyse**: `transSizeInfo.gridDepth()` retourne la dimension Z du bounding box au yaw TRANSFORME. Mais un banc 2x1x1 au yaw=3 a son width (2) aligne en Z → `Box.depth() = 2` → `gridDepth = 2` au lieu de 1. La compensation `worldX -= 1` decale les bancs alors qu'ils ne devraient pas etre affectes.

**Bug**: `gridDepth()` du `BlockSizeInfo` depend du yaw. Ce n'est PAS la profondeur conceptuelle du bloc mais la dimension Z du bounding box qui tourne avec le yaw.

## v9 - Utiliser dimensions conceptuelles (yaw=0) pour compensation (2026-02-11 ~11:15)
**Approche**: Meme logique que v8, mais utiliser `getBlockSize(blockType, 0)` (yaw=0 = rotation de base) pour obtenir les dimensions CONCEPTUELLES fixes du bloc, independantes de la rotation.

**Principe cle**: Au yaw 0, les dimensions du bounding box correspondent aux dimensions conceptuelles:
- `gridWidth()` = taille sur l'axe principal X (width)
- `gridDepth()` = taille sur l'axe transversal Z (depth)
- `gridHeight()` = taille verticale Y (height)

Pour un banc 2x1x1: `gridDepth(yaw=0) = 1` → compensation = 0 → pas de decalage. OK !
Pour un lit 2x2x3: `gridDepth(yaw=0) = 3` → compensation = 2 → decalage correct. OK !

Le probleme de v8 etait que `gridDepth(yaw=3)` pour un banc retournait 2 (le width tourne en Z).

**Resultat**: ECHEC - Bancs OK (conceptDepth=1, pas de compensation) mais lits ont une rotation inversee sur les 3 pastes (flipX, rot180, flipZ). La compensation transversale decale les lits de leur position correcte.

**Analyse fondamentale**: La compensation transversale est FAUSSE. Quand on swap yaw 0→2 (flipX), le filler change de direction en Z (de +Z a -Z). Mais dans un miroir X, l'axe Z n'est PAS affecte → le filler en Z inversee EST le comportement correct du miroir. L'origin est deja a la bonne position (transformee par le miroir du clipboard). Compenser en Z decale le lit de sa position miroir correcte.

## v10 - Retour v7 : AUCUNE compensation flipX/flipZ, investigation bancs flipZ (2026-02-11 ~11:30)
**Approche**: Suppression totale de la compensation de position pour flipX et flipZ. Retour a l'approche v7 qui etait quasi-parfaite. Seul vFlip garde sa compensation (filler Y toujours en +Y).

**Raisonnement**: Les v8 et v9 tentaient de compenser le changement de direction transversale du filler, mais c'etait une erreur logique. Le miroir transforme les coordonnees → le yaw swap change la direction du filler → le filler s'etend dans la direction miroir = CORRECT. Pas besoin de compenser.

**Probleme restant de v7**: 2 bancs manquants dans le flipZ. Ce n'est PAS un probleme de compensation (les bancs 2x1x1 ont depth=1, compensation=0). Hypotheses:
1. Collision filler : un bloc solide pose APRES le banc ecrase son filler
2. Hytale refuse de creer le filler si un bloc existe deja a cette position
3. Probleme d'ordre d'iteration dans la 2eme passe (solides)

**Resultat**: ECHEC - Meme probleme que v7. Les lits au yaw 0/2 ne sont pas swappes lors du flipZ (car multipart flipZ ne faisait que 1<->3). Le lit garde yaw=2 apres flipZ → filler en -Z au lieu de +Z.

**Analyse fondamentale (revision)**: L'approche "swap axe uniquement" (v5/v7/v10) est INCOMPLETE. Un miroir inverse un axe du monde. Pour les multipart, TOUTES les directions du filler qui passent par cet axe doivent etre inversees. Un yaw encode DEUX directions (width ET depth). Le swap d'un seul couple (ex: 0<->2 pour flipX) n'inverse que les yaw dont la width est sur cet axe, mais ignore les yaw dont le DEPTH est sur cet axe.

Analyse par yaw pour comprendre quel swap est necessaire :
```
Yaw | W    | D    | FlipX need      | FlipZ need
 0  | +X   | +Z   | W=-X → yaw 2   | D=-Z → yaw 2 (W change aussi!)
 1  | +Z   | -X   | D=+X → yaw 3   | W=-Z → yaw 3 (D change aussi!)
 2  | -X   | -Z   | W=+X → yaw 0   | D=+Z → yaw 0 (W change aussi!)
 3  | -Z   | +X   | D=-X → yaw 1   | W=+Z → yaw 1 (D change aussi!)
```

Resultat : pour FlipX ET FlipZ, le swap est TOUJOURS 0<->2 ET 1<->3 (swap complet). Mais le swap complet inverse aussi la dimension NON-miroir → besoin de COMPENSATION.

## v11 - Swap complet + compensation effet secondaire (2026-02-11 ~11:50)
**Approche**: Pour les multipart, swap COMPLET (0<->2 ET 1<->3) pour flipX et flipZ. Plus compensation de position pour l'axe NON-miroir qui s'inverse comme effet secondaire.

**Swap complet**: Identique pour flipX et flipZ: `0<->2, 1<->3`. C'est comme une rotation 180° du yaw. La difference entre flipX et flipZ est dans la COMPENSATION.

**Compensations**: Utilise les dimensions conceptuelles au yaw=0 (cW=width, cD=depth).

FlipX (miroir X gere l'inversion X, compenser l'inversion Z):
- swap 0→2: D +Z→-Z → `worldZ += (cD-1)`
- swap 2→0: D -Z→+Z → `worldZ -= (cD-1)`
- swap 1→3: W +Z→-Z → `worldZ += (cW-1)`
- swap 3→1: W -Z→+Z → `worldZ -= (cW-1)`

FlipZ (miroir Z gere l'inversion Z, compenser l'inversion X):
- swap 0→2: W +X→-X → `worldX += (cW-1)`
- swap 2→0: W -X→+X → `worldX -= (cW-1)`
- swap 1→3: D -X→+X → `worldX -= (cD-1)`
- swap 3→1: D +X→-X → `worldX += (cD-1)`

**Verification banc (2x1x1, cW=2, cD=1)**:
- FlipX yaw 1→3: `worldZ += (2-1) = +1` ← NOUVEAU par rapport a v7
- FlipZ yaw 1→3: `worldX -= (1-1) = 0` ← pas d'effet (comme v7)

**Verification lit (2x2x3, cW=2, cD=3)**:
- FlipZ yaw 2→0: `worldX -= (2-1) = -1` ← corrige le decalage X
- FlipX yaw 2→0: `worldZ -= (3-1) = -2` ← corrige le decalage Z

**Resultat**: PRESQUE - Lits correctement en miroir ! Mais 3 problemes restants :
1. Banc yaw=0 dans flipZ swappe en yaw=2 + comp X += 1 → deporte (le banc est SYMMETRIC, pas besoin de swap 0<->2)
2. 2 bancs manquants dans flipZ (meme probleme que v7)
3. Bancs flipX au yaw=1 swappes en yaw=3 + comp Z += 1 → potentiellement deporte aussi

**Analyse**: Le swap 0<->2 dans flipZ et le swap 1<->3 dans flipX ne sont necessaires que pour les blocs avec conceptDepth > 1. Pour depth=1, l'inversion de la direction du depth n'a aucun effet (1 seul bloc). Donc le swap secondaire est inutile et la compensation deplace le bloc.

## v12 - Swap conditionnel: secondaire seulement si cD > 1 (2026-02-11 ~12:10)
**Approche**: Swap CONDITIONNEL pour les multipart :

FlipX:
- swap 0<->2 TOUJOURS (width en X = axe miroir)
- swap 1<->3 SEULEMENT si conceptDepth > 1

FlipZ:
- swap 1<->3 TOUJOURS (width en Z = axe miroir)
- swap 0<->2 SEULEMENT si conceptDepth > 1

**Effet par bloc**:
- Banc (cW=2, cD=1): flipX = swap 0<->2 only, flipZ = swap 1<->3 only → COMME V7 (qui marchait pour les bancs)
- Lit (cW=2, cD=3): swap complet 0<->2 + 1<->3 + compensations → COMME V11 (qui marchait pour les lits)

**Resultat**: flipX OK, rot180 OK. FlipZ = 2 bancs manquants (meme probleme persistant depuis v7).

**Analyse du probleme persistant "2 bancs manquants en flipZ"** :
Tous les 5 bancs sont bien dans les logs de paste (0 failures). Le probleme est une COLLISION DE FILLERS.

**Cause racine** : Le paste itere en ordre cx=0→11, cy=0→9, cz=0→11. Les blocs solides sont tous dans la meme passe (air d'abord, solides ensuite). Quand un banc est place avec yaw=3 (filler en -Z), le filler est cree a Z-1. Mais un bloc de sol (`Soil_Clay_Smooth_Lime`) a une position de clipboard mappee a cette position de filler est paste APRES le banc (car cx ou cz plus grand). Le `world.setBlock()` du sol ECRASE le filler du banc, et Hytale detruit automatiquement le banc entier quand son filler est ecrase.

Exemple concret :
- 5eme banc (yaw=0) au clipboard (7,1,6) → world (-226,270,-191), filler a (-225,270,-191)
- Un bloc de sol au clipboard (8,1,6) → world (-225,270,-191) = meme position que le filler !
- Le sol est paste APRES le banc (cx=8 > cx=7), ecrase le filler → banc supprime.

## v13 - Paste en 3 passes: air → solides → multipart (2026-02-11 ~12:30)
**Approche**: Systeme de paste a 3 passes au lieu de 2 :
1. **Air** (passe 1) - nettoie la zone
2. **Solides non-multipart** (passe 2) - sol, murs, decorations normales
3. **Multipart** (passe 3) - bancs, lits, lanternes EN DERNIER

En placant les multipart en dernier, leurs fillers auto-crees par Hytale ne peuvent plus etre ecrases par d'autres blocs de la meme operation de paste.

**Detection multipart**: Utilise `BlockSizeHelper.getBlockSize(blockType, 0)` pour detecter si un bloc est multipart. Le test `checkSize.isMultiPart()` utilise `protrudesUnitBox || width > 1 || height > 1 || depth > 1`.

**Compensation multipart**: Deplacee de la passe 2 (ou elle etait du code mort) vers la passe 3 uniquement.

**Log ameliore**: Le message de total indique maintenant `(air: N, solids: N, multipart: N)`.

**Resultat**: Bancs OK pour les 3 pastes ! Mais les lits sont decales.

## v14 - Correction d'ordre des passes (2026-02-11 ~13:00)
**Correction**: L'ordre des passes etait air → solids → multipart, mais les
multipart doivent etre places APRES les solides pour que les fillers ne soient
pas ecrases. Correction de l'ordre d'iteration.

**Resultat**: Bancs OK (plus de bancs manquants). Lits decales de 1-2 blocs.

## v15-v16 - Tentatives de compensation position lits
Plusieurs ajustements de compensation de position pour les lits.
Problemes : certains yaw compenses, d'autres pas.

## v17 - Ordre definitif AIR → MULTIPART → SOLIDS
**Changement cle**: L'ordre correct est :
1. **AIR** — effacer les anciens fillers et blocs
2. **MULTIPART** — placer les blocs multipart (fillers auto-crees)
3. **SOLIDS** — placer les blocs normaux (ne peuvent plus ecraser les fillers car tout est deja la)

**Resultat**: Bancs parfaits pour flipX et rot180. Lits encore decales.

## v18 - Premier support de compensation pour lits (cD>1)
Ajout de compensation cD-1 pour les cas ou le depth est inverse.
Marche pour flipX mais pas pour flipZ (cas yaw manquants).

---

## v19-v20 - RESOLUTION COMPLETE (session finale)

### Insight fondamental
Pour les blocs avec **conceptDepth > 1** (lits), le swap de yaw doit etre
DIFFERENT de celui des blocs cD=1 (bancs).

**Principe** : Swap le yaw **UNIQUEMENT quand la profondeur est alignee avec l'axe du flip**.

Quand la profondeur N'EST PAS sur l'axe du flip, elle n'est pas affectee
→ pas besoin de swap.

### Tableau de decisions yaw

| Flip | Yaw orig | Direction depth | Sur l'axe du flip ? | Swap ? | Nouveau yaw |
|------|----------|-----------------|---------------------|--------|-------------|
| flipX | 0 | D=+Z | Non (Z != X) | Non | 0 |
| flipX | 1 | D=-X | Oui (X = X) | Oui | 3 |
| flipX | 2 | D=-Z | Non (Z != X) | Non | 2 |
| flipX | 3 | D=+X | Oui (X = X) | Oui | 1 |
| flipZ | 0 | D=+Z | Oui (Z = Z) | Oui | 2 |
| flipZ | 1 | D=-X | Non (X != Z) | Non | 1 |
| flipZ | 2 | D=-Z | Oui (Z = Z) | Oui | 0 |
| flipZ | 3 | D=+X | Non (X != Z) | Non | 3 |

**Comparaison avec cD=1 (banc)** :
- Banc flipX : swap 0↔2 (width sur X)
- Banc flipZ : swap 1↔3 (width sur Z)
- Lit flipX : swap 1↔3 (depth sur X) ← **INVERSE du banc !**
- Lit flipZ : swap 0↔2 (depth sur Z) ← **INVERSE du banc !**

### Tentative 1 — Yaw correct, position incomplete
Apres implementation du swap conditionnel, le yaw est correct mais les lits
sont decales de 1 bloc.

**Cause** : Quand le yaw change, la direction du **width** change aussi. L'origin
du bloc se deplace d'un coin a l'autre → il faut compenser de `cW-1`.

### Tentative 2 — Compensation des deux cas
Ajout de compensation width pour les deux cas :
- Yaw inchange : compenser width sur l'axe du flip
- Yaw swappe : compenser width sur l'axe transversal

**Erreur** : Les signes de compensation pour le cas swappe etaient inverses.

### Tentative 3 — SOLUTION FINALE (signes corrects)

**Insight mathematique** : Quand le yaw est swappe (ex: 0→2), l'origin du bloc
se deplace d'un "coin" a l'autre :
- origYaw=0 → transYaw=2 : origin passe de minX a maxX → `worldX += (cW-1)`
- origYaw=2 → transYaw=0 : origin passe de maxX a minX → `worldX -= (cW-1)`

Les signes de la tentative 2 etaient l'exact **inverse**.

**Resultat** : **"TOP DU TOP !"** — Tout fonctionne parfaitement pour les 4 pastes.

---

## Algorithme final complet

### 1. transformRotation() — Calcul du nouveau yaw

```
SI flipX ET multipart:
    SI cD <= 1 (banc):
        swap 0 ↔ 2     // width sur X, il faut inverser
    SI cD > 1 (lit):
        swap 1 ↔ 3     // depth sur X (yaw 1/3), inverser depth direction
        // yaw 0/2: depth sur Z, pas affecte par flipX → pas de swap

SI flipZ ET multipart:
    SI cD <= 1 (banc):
        swap 1 ↔ 3     // width sur Z, il faut inverser
    SI cD > 1 (lit):
        swap 0 ↔ 2     // depth sur Z (yaw 0/2), inverser depth direction
        // yaw 1/3: depth sur X, pas affecte par flipZ → pas de swap
```

### 2. Compensation de position — Ajustement coordonnees monde

```
SI flipX:
    SI yaw inchange (origYaw == transYaw) ET cD > 1:
        // Depth pas sur X, compenser width sur X
        yaw 0: worldX -= (cW - 1)    // W=+X, origin a minX
        yaw 2: worldX += (cW - 1)    // W=-X, origin a maxX

    SI yaw swappe (origYaw != transYaw) ET cD > 1:
        // Depth sur X, compenser width sur Z (axe transversal)
        origYaw 1 → 3: worldZ += (cW - 1)  // origin: minZ → maxZ
        origYaw 3 → 1: worldZ -= (cW - 1)  // origin: maxZ → minZ

SI flipZ:
    SI yaw inchange (origYaw == transYaw) ET cD > 1:
        // Depth pas sur Z, compenser width sur Z
        yaw 1: worldZ -= (cW - 1)    // W=+Z, origin a minZ
        yaw 3: worldZ += (cW - 1)    // W=-Z, origin a maxZ

    SI yaw swappe (origYaw != transYaw) ET cD > 1:
        // Depth sur Z, compenser width sur X (axe transversal)
        origYaw 0 → 2: worldX += (cW - 1)  // origin: minX → maxX
        origYaw 2 → 0: worldX -= (cW - 1)  // origin: maxX → minX
```

### 3. Passes de placement

```
Passe 1: AIR     — Effacer tous les blocs de la zone
Passe 2: MULTIPART — Placer les blocs multipart (fillers auto-crees)
Passe 3: SOLIDS    — Placer les blocs normaux 1x1x1
```

---

## Explication intuitive

### Pourquoi le yaw conditionnel ?
Un lit (cD=3) a yaw=0 a sa profondeur en +Z. Si on fait un flipX (miroir X),
l'axe Z n'est pas affecte → la profondeur reste dans la bonne direction →
pas besoin de swap.

Mais si ce lit est a yaw=1, la profondeur va en -X. Un flipX inverse l'axe X,
donc la profondeur doit pointer dans l'autre direction → swap 1↔3.

### Pourquoi la compensation de position ?
Quand on place un lit a yaw=0, l'origin est au coin (minX, minZ) et les fillers
s'etendent vers +X et +Z. Apres un flipX sans swap (yaw reste 0), le transform
a inverse la position sur X. La largeur du bloc (cW=2) fait que l'origin est
decalee → on compense avec `worldX -= (cW-1)`.

Quand le yaw est swappe (ex: flipZ, yaw 0→2), le width change de direction
(de +X a -X). L'origin passe du coin minX au coin maxX → on compense
avec `worldX += (cW-1)`.

---

## Lecons apprises

1. **Ne PAS copier les swaps entre cD=1 et cD>1** — logique INVERSE
2. **La compensation a DEUX cas** : yaw inchange + yaw swappe
3. **Les signes dependent du mouvement de l'origin** : min→max = positif, max→min = negatif
4. **Tester les 4 yaw x 2 flips** = 8 combinaisons minimum
5. **Les fillers n'ont pas besoin d'etre geres** — `world.setBlock()` les recree
6. **Utiliser dimensions conceptuelles (yaw=0)** — `getBlockSize(blockType, 0)`
7. **Ordre des passes** : AIR → MULTIPART → SOLIDS pour eviter les collisions de fillers

## Fichiers modifies

| Fichier | Description |
|---------|-------------|
| `ClipboardOperations.java` | Logique principale paste/flip — `transformRotation()` + compensation position |
| `BlockSizeHelper.java` | Utilitaire pour obtenir les dimensions a yaw=0 (reference seulement) |
