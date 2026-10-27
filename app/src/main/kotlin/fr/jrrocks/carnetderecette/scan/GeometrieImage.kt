package fr.jrrocks.carnetderecette.scan

import fr.jrrocks.carnetderecette.core.scan.ElementLu

/** Un rectangle : en pixels d'une image, ou en fractions (0 à 1) d'une autre zone. */
data class Zone(val gauche: Float, val haut: Float, val droite: Float, val bas: Float) {
    val largeur: Float get() = droite - gauche
    val hauteur: Float get() = bas - haut

    fun contient(x: Float, y: Float): Boolean = x in gauche..droite && y in haut..bas

    /** La part de cette zone décrite par [fractions] (fractions de sa largeur et de sa hauteur). */
    fun partie(fractions: Zone): Zone = Zone(
        gauche + fractions.gauche * largeur,
        haut + fractions.haut * hauteur,
        gauche + fractions.droite * largeur,
        haut + fractions.bas * hauteur,
    )
}

/**
 * Le passage de l'image du capteur à l'écran, sans Android : de quoi le tester sur la JVM.
 *
 * Une image d'analyse arrive **couchée** dans son tampon ([largeur] × [hauteur]) ; il faut la
 * tourner de `rotationDegrees` dans le sens horaire pour la voir droite. ML Kit rend ses
 * rectangles dans l'image **droite** : à 90° ou 270°, sa largeur et sa hauteur s'inversent.
 * Le recadrage de CameraX (`ImageProxy.cropRect`, la part de l'image que l'aperçu montre à
 * l'écran) est, lui, donné dans le tampon.
 */
object GeometrieImage {

    /** Largeur et hauteur de l'image une fois droite. */
    fun dimensionsDroites(largeur: Int, hauteur: Int, rotation: Int): Pair<Int, Int> =
        if (quart(rotation) % 2 == 1) hauteur to largeur else largeur to hauteur

    /** Un rectangle du tampon, exprimé dans l'image droite. */
    fun versDroite(zone: Zone, largeur: Int, hauteur: Int, rotation: Int): Zone {
        val l = largeur.toFloat()
        val h = hauteur.toFloat()
        return when (quart(rotation)) {
            // Un quart de tour horaire : le haut du tampon devient la droite de l'image.
            1 -> Zone(h - zone.bas, zone.gauche, h - zone.haut, zone.droite)
            2 -> Zone(l - zone.droite, h - zone.bas, l - zone.gauche, h - zone.haut)
            3 -> Zone(zone.haut, l - zone.droite, zone.bas, l - zone.gauche)
            else -> zone
        }
    }

    /** L'inverse de [versDroite] : un rectangle de l'image droite, exprimé dans le tampon. */
    fun versTampon(zone: Zone, largeur: Int, hauteur: Int, rotation: Int): Zone {
        val (largeurDroite, hauteurDroite) = dimensionsDroites(largeur, hauteur, rotation)
        return versDroite(zone, largeurDroite, hauteurDroite, (360 - quart(rotation) * 90) % 360)
    }

    /**
     * Le réticule dans l'image droite. [reticule] est en fractions de l'aperçu affiché, qui
     * montre exactement [recadrage] (en pixels du tampon) : c'est la même zone que celle dessinée
     * à l'écran.
     */
    fun reticuleDansImage(largeur: Int, hauteur: Int, rotation: Int, recadrage: Zone, reticule: Zone): Zone =
        versDroite(recadrage, largeur, hauteur, rotation).partie(reticule)

    /**
     * L'élément lu dans [boite] (pixels de l'image droite), rapporté au [reticule] : `null` si
     * son centre est hors du réticule. Hauteur et position deviennent des parts du réticule
     * (`ElementLu`), comparables d'un téléphone à l'autre.
     */
    fun elementLu(texte: String, boite: Zone, reticule: Zone): ElementLu? {
        val centreX = (boite.gauche + boite.droite) / 2
        val centreY = (boite.haut + boite.bas) / 2
        if (!reticule.contient(centreX, centreY) || reticule.largeur <= 0f || reticule.hauteur <= 0f) return null
        return ElementLu(
            texte = texte,
            hauteur = boite.hauteur / reticule.hauteur,
            x = (centreX - reticule.gauche) / reticule.largeur,
            y = (centreY - reticule.haut) / reticule.hauteur,
        )
    }

    /**
     * La rotation cible d'une photo selon l'orientation **physique** du téléphone, en degrés
     * (`OrientationEventListener` : 0 à 359, ou -1 posé à plat) : 0 à 3 quarts de tour, les
     * valeurs de `Surface.ROTATION_0` à `ROTATION_270`, comme dans la doc CameraX. `null` quand
     * l'orientation est inconnue : on garde la précédente. Sert quand la rotation automatique
     * de l'écran est coupée : l'écran reste en portrait, la page se cadre en paysage.
     */
    fun rotationCible(orientation: Int): Int? = when (orientation) {
        in 45 until 135 -> 3
        in 135 until 225 -> 2
        in 225 until 315 -> 1
        in 0 until 45, in 315 until 360 -> 0
        else -> null
    }

    /** 0, 90, 180 ou 270 degrés → 0 à 3 quarts de tour (les autres valeurs n'existent pas chez CameraX). */
    private fun quart(rotation: Int): Int = (((rotation / 90) % 4) + 4) % 4
}
