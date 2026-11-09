package fr.jrrocks.carnetderecette.core.cuisine

import kotlinx.serialization.Serializable

/**
 * Ce que le téléphone retient de la cuisine d'une semaine (`cuisine.json`, à côté de la semaine
 * importée) : les recettes cuisinées jusqu'au bout. « Ce soir » propose la suivante.
 */
@Serializable
data class SuiviCuisine(val cuisinees: List<Int> = emptyList())
