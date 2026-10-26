package fr.jrrocks.carnetderecette.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Le calendrier de la semaine. Tant que rien n'est importé (lots 2 à 4), la Semaine regarde
 * vers le prochain vendredi de livraison : c'est la prochaine chose à faire. Les lots
 * suivants la caleront sur les données du Drive.
 */

/** Le vendredi de livraison qui vient (aujourd'hui si l'on est vendredi). */
fun prochainVendredi(jour: LocalDate): LocalDate = jour.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))

/** Le numéro de semaine des agendas (44), le même que dans les fichiers `semaine-AAAA-Sww`. */
fun numeroSemaine(jour: LocalDate): Int = jour.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

private val JOUR_COURT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

/** « 30 oct. » */
fun dateCourte(jour: LocalDate): String = jour.format(JOUR_COURT)
