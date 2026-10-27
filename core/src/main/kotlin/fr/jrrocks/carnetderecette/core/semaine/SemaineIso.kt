package fr.jrrocks.carnetderecette.core.semaine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Une semaine des agendas (ISO 8601), écrite « 2026-W44 » : la forme des adresses de menu de
 * HelloFresh (`/menus/2026-W44`) et des fichiers du Drive (`recettes-2026-W44.json`). Le menu
 * d'une semaine est celui des box livrées le vendredi de cette semaine.
 */
@Serializable(with = SemaineIso.Serialiseur::class)
data class SemaineIso(val annee: Int, val numero: Int) : Comparable<SemaineIso> {

    init {
        require(numero in 1..53) { "Numéro de semaine hors limites : $numero" }
    }

    override fun toString(): String = "%04d-W%02d".format(annee, numero)

    override fun compareTo(other: SemaineIso): Int = compareValuesBy(this, other, { it.annee }, { it.numero })

    fun lundi(): LocalDate = LocalDate.of(annee, 1, 4)
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, numero.toLong())
        .with(DayOfWeek.MONDAY)

    fun vendredi(): LocalDate = lundi().plusDays(4)

    companion object {
        fun de(jour: LocalDate): SemaineIso =
            SemaineIso(jour.get(IsoFields.WEEK_BASED_YEAR), jour.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

        /** « 2026-W44 » ; `null` si le texte n'a pas cette forme. */
        fun lire(texte: String): SemaineIso? {
            val morceaux = texte.trim().uppercase().split("-W")
            if (morceaux.size != 2) return null
            val annee = morceaux[0].toIntOrNull() ?: return null
            val numero = morceaux[1].toIntOrNull() ?: return null
            return if (numero in 1..53) SemaineIso(annee, numero) else null
        }
    }

    object Serialiseur : KSerializer<SemaineIso> {
        override val descriptor = PrimitiveSerialDescriptor("SemaineIso", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: SemaineIso) = encoder.encodeString(value.toString())
        override fun deserialize(decoder: Decoder): SemaineIso {
            val texte = decoder.decodeString()
            return lire(texte) ?: throw IllegalArgumentException("Semaine illisible : $texte")
        }
    }
}
