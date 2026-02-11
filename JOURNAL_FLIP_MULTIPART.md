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

**Resultat**: EN TEST
