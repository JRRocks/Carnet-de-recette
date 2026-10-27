package fr.jrrocks.carnetderecette.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Le calendrier de la semaine. Une semaine du Carnet va du vendredi de livraison au jeudi
 * suivant : du samedi au jeudi, on reste dans la semaine du dernier vendredi, celle des box
 * qu'on cuisine (le samedi, on importe ses recettes ; du lundi au jeudi, on les cuisine). Elle
 * ne passe à la suivante que le vendredi de la livraison d'après. Tout part de ce vendredi de
 * référence : le titre « Semaine N », les dates de la ligne et les données (box scannées,
 * recettes, scanner, fiche papier, séance), rangées sous la semaine de leur livraison.
 */

/** Le vendredi de livraison de la semaine en cours : aujourd'hui si c'est vendredi, sinon le dernier passé. */
fun vendrediDeReference(jour: LocalDate): LocalDate = jour.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))

/** Le numéro de semaine des agendas (44), le même que dans les fichiers `semaine-AAAA-Sww`. */
fun numeroSemaine(jour: LocalDate): Int = jour.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

private val JOUR_COURT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val MOIS_COURT = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH)

/** « 30 oct. », et « 1er nov. » pour le premier du mois. */
fun dateCourte(jour: LocalDate): String =
    if (jour.dayOfMonth == 1) "1er\u00A0" + jour.format(MOIS_COURT) else jour.format(JOUR_COURT).replace(' ', '\u00A0')

/** « Semaine 44 » : le titre de l'accueil, calé sur le même vendredi que la ligne. */
fun titreSemaine(jour: LocalDate): String = "Semaine ${numeroSemaine(vendrediDeReference(jour))}"
