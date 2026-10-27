package fr.jrrocks.carnetderecette.scan

import java.io.File
import java.io.FileNotFoundException

/**
 * Le rangement des photos de la fiche papier, sans Android : de quoi le tester sur la JVM.
 *
 * L'enregistrement d'une fiche se fait en deux temps, chacun rejouable sans rien perdre :
 * 1. chaque photo passe du cache à côté du fichier de la semaine, sous un nom **en attente**
 *    ([enAttente]) : la fiche précédente de la même box garde ses photos tant que la nouvelle
 *    n'est pas inscrite ;
 * 2. une fois la fiche inscrite dans le dépôt, la photo en attente prend son nom définitif,
 *    et remplace d'un coup celle de la fiche précédente.
 *
 * Un essai qui échoue entre les deux (téléphone plein à l'écriture du fichier de la semaine)
 * se reprend tel quel : [deplacer] ne refait rien de ce qui est déjà fait, et ne supprime
 * jamais une photo dont la source a disparu.
 */
object RangementFiche {

    /** Le suffixe des photos rangées mais pas encore inscrites dans le dépôt. */
    const val SUFFIXE_ATTENTE = ".en-attente"

    /** Le nom en attente de la photo [nom]. */
    fun enAttente(nom: String): String = nom + SUFFIXE_ATTENTE

    /**
     * Déplace [source] en [cible], en remplaçant une éventuelle [cible] : un renommage quand
     * les deux sont sur le même volume (le cas du cache et des fichiers de l'app), sinon une
     * copie. Idempotent :
     * - [source] et [cible] identiques : rien à faire ;
     * - [source] disparue mais [cible] présente : déjà déplacée par un essai précédent ;
     * - les deux absentes : [PhotoIntrouvable], sans rien toucher.
     */
    fun deplacer(source: File, cible: File) {
        if (source.absoluteFile == cible.absoluteFile) return
        if (!source.exists()) {
            if (cible.exists()) return
            throw PhotoIntrouvable(source.name)
        }
        // rename(2) remplace la cible d'un seul coup ; sinon (autre volume), copie puis suppression.
        if (source.renameTo(cible)) return
        source.copyTo(cible, overwrite = true)
        source.delete()
    }

    /** Les photos en attente de [dossier], laissées par un enregistrement interrompu (processus tué). */
    fun oubliees(dossier: File): List<File> =
        dossier.listFiles { fichier -> fichier.isFile && fichier.name.endsWith(SUFFIXE_ATTENTE) }.orEmpty().toList()
}

/** La photo à ranger n'est plus là (cache vidé par le système) : il faut la reprendre. */
class PhotoIntrouvable(nom: String) : FileNotFoundException("Photo introuvable\u00A0: $nom")
