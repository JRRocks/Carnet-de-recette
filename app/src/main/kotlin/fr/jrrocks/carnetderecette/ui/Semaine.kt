package fr.jrrocks.carnetderecette.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Le calendrier de la semaine. Une semaine du Carnet va du vendredi de livraison au jeudi
 * suivant : le samedi et le dimanche appartiennent encore à la semaine du vendredi qui vient
 * de passer (le samedi, on importe ses recettes). Les autres jours regardent vers le
 * vendredi qui vient. Le titre « Semaine N » et les dates de la ligne partent tous de ce
 * vendredi de référence. Les lots suivants caleront l'étape courante sur les données du Drive.
 */

/** Le vendredi de livraison de la semaine affichée. */
fun vendrediDeReference(jour: LocalDate): LocalDate = when (jour.dayOfWeek) {
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> jour.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
    else -> jour.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
}

/** Le numéro de semaine des agendas (44), le même que dans les fichiers `semaine-AAAA-Sww`. */
fun numeroSemaine(jour: LocalDate): Int = jour.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

private val JOUR_COURT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val MOIS_COURT = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH)

/** « 30 oct. », et « 1er nov. » pour le premier du mois. */
fun dateCourte(jour: LocalDate): String =
    if (jour.dayOfMonth == 1) "1er\u00A0" + jour.format(MOIS_COURT) else jour.format(JOUR_COURT).replace(' ', '\u00A0')

/** « Semaine 44 » : le titre de l'accueil, calé sur le même vendredi que la ligne. */
fun titreSemaine(jour: LocalDate): String = "Semaine ${numeroSemaine(vendrediDeReference(jour))}"
