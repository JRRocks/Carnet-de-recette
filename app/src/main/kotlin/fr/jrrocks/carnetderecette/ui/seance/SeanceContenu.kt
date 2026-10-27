package fr.jrrocks.carnetderecette.ui.seance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.ui.composants.AXE
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.DEBUT_TEXTE
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse
import fr.jrrocks.carnetderecette.ui.composants.Etape
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.LigneDeTemps
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat
import fr.jrrocks.carnetderecette.ui.composants.marqueur
import fr.jrrocks.carnetderecette.ui.composants.troncon
import fr.jrrocks.carnetderecette.ui.theme.chiffres

/*
 * La séance en plein écran (direction « Correspondance », écran Séance) : le schéma de ligne
 * réduit au voisinage du segment courant (le dernier fait, le courant en grand, les deux
 * suivants), dans l'ordre où la voix les dira ; en bas, le dock à portée de pouce : « Relire »
 * et le minuteur (72 dp), puis « C'est fait » sur toute la largeur. Pas d'« Étape 3/7 » ni de
 * sur-titre : la place dans la recette se voit sur le trait.
 */

/** Largeur de lecture maximale, centrée, sur un écran large ou en paysage. */
private val LARGEUR_CONTENU = 640.dp

/** Au-dessus du premier titre d'une rangée, comme dans `LigneDeTemps`. */
private val HAUT = 12.dp

/** Composable sans état : le ViewModel et les captures lui donnent [etat], il remonte les gestes par [actions]. */
@Composable
fun SeanceContenu(etat: EtatSeance, actions: ActionsSeance, modifier: Modifier = Modifier) {
    EcranPousse(
        titre = etat.titre,
        onRetour = actions.revenir,
        modifier = modifier,
        actions = { ActionsDeBarre(etat, actions) },
    ) { marges ->
        Box(Modifier.fillMaxSize().padding(marges), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = LARGEUR_CONTENU).fillMaxSize()) {
                if (etat.terminee) {
                    FinDeSeance(etat, actions, Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        LignesEtat(etat, actions)
                        Schema(etat, actions)
                    }
                    Dock(etat, actions)
                }
            }
        }
    }
}

@Composable
private fun ActionsDeBarre(etat: EtatSeance, actions: ActionsSeance) {
    IconButton(onClick = actions.basculerVoix) {
        Icon(
            painterResource(if (etat.voixCoupee) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
            contentDescription = if (etat.voixCoupee) "Rétablir la voix" else "Couper la voix",
        )
    }
    val ecoute = etat.micro == EtatEcoute.Ecoute
    IconButton(onClick = actions.basculerMicro) {
        Icon(
            painterResource(if (ecoute) R.drawable.ic_mic else R.drawable.ic_mic_off),
            contentDescription = if (ecoute) "Arrêter d'écouter" else "Écouter les commandes vocales",
        )
    }
}

// ── Lignes d'état ───────────────────────────────────────────────────────────────

/** Au plus deux lignes : ce qui empêche la voix, puis le micro, puis la pause, puis la voix coupée. */
@Composable
private fun LignesEtat(etat: EtatSeance, actions: ActionsSeance) {
    val lignes = buildList<@Composable (Modifier) -> Unit> {
        when (etat.voix) {
            EtatVoix.LANGUE_ABSENTE -> add {
                LigneEtat(R.drawable.ic_volume_off, "Installe une voix française pour la lecture à voix haute.", TonEtat.INFO, it) {
                    BoutonCarnet("Installer une voix", actions.reglerVoix)
                }
            }
            EtatVoix.INDISPONIBLE -> add {
                LigneEtat(
                    R.drawable.ic_error,
                    "Aucune synthèse vocale ne répond sur ce téléphone : les étapes restent à l'écran.",
                    TonEtat.ERREUR,
                    it,
                ) { BoutonCarnet("Réglages de la voix", actions.reglerVoix, sorte = SorteBouton.CONTOUR) }
            }
            EtatVoix.PREPARATION, EtatVoix.PRETE -> Unit
        }
        when (val micro = etat.micro) {
            EtatEcoute.PermissionRequise -> add {
                LigneEtat(
                    R.drawable.ic_mic_off,
                    "Le micro n'est pas autorisé. Pour dire « suivant » sans toucher l'écran, autorise-le dans les réglages du téléphone.",
                    TonEtat.INFO,
                    it,
                ) { BoutonCarnet("Ouvrir les réglages", actions.reglerMicro) }
            }
            EtatEcoute.Indisponible -> add {
                LigneEtat(
                    R.drawable.ic_mic_off,
                    "Pas de reconnaissance vocale sur ce téléphone : les boutons font la même chose.",
                    TonEtat.NEUTRE,
                    it,
                )
            }
            is EtatEcoute.Erreur -> add {
                LigneEtat(R.drawable.ic_error, micro.motif, TonEtat.ERREUR, it) {
                    BoutonCarnet("Réessayer", actions.basculerMicro, sorte = SorteBouton.CONTOUR)
                }
            }
            EtatEcoute.Arretee, EtatEcoute.Ecoute -> Unit
        }
        if (etat.enPause) {
            add {
                LigneEtat(R.drawable.ic_pause, "Lecture en pause. Les minuteurs continuent.", TonEtat.NEUTRE, it) {
                    BoutonCarnet("Reprendre", actions.reprendre, icone = R.drawable.ic_play_arrow)
                }
            }
        }
        if (etat.voixCoupee) {
            add {
                LigneEtat(R.drawable.ic_volume_off, "Voix coupée : les étapes s'affichent sans être lues.", TonEtat.NEUTRE, it) {
                    BoutonCarnet("Rétablir la voix", actions.basculerVoix, sorte = SorteBouton.TEXTE)
                }
            }
        }
    }
    lignes.take(2).forEach { ligne -> ligne(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
}

// ── Le schéma ───────────────────────────────────────────────────────────────────

@Composable
private fun Schema(etat: EtatSeance, actions: ActionsSeance) {
    // Le trait vient d'en haut si des segments précèdent le premier montré, et file vers le bas
    // tant qu'il en reste.
    val premier = etat.precedent ?: etat.courant
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
        etat.precedent?.let { RangeeFaite(it, traitAuDessus = it.numero > 1, actions) }
        etat.courant?.let {
            RangeeCourante(it, traitAuDessus = premier != it || it.numero > 1, traitAuDessous = etat.suivants.isNotEmpty(), actions)
        }
        etat.suivants.forEachIndexed { i, segment ->
            RangeeAVenir(segment, traitAuDessous = i < etat.suivants.lastIndex || etat.restants > 0, actions)
        }
        if (etat.restants > 0) RangeeReste(etat.restants)
    }
}

/**
 * Une rangée du schéma : son tronçon de trait et son marqueur, dessinés derrière elle
 * (`drawBehind`) pour suivre la hauteur réelle du texte, jusqu'à 200 %. Le marqueur s'aligne
 * sur le milieu de la première ligne de [styleTitre].
 */
@Composable
private fun RangeeSchema(
    etatMarqueur: EtatEtape?,
    styleTitre: TextStyle,
    traitAuDessus: Boolean,
    traitAuDessous: Boolean,
    etatLu: String,
    modifier: Modifier = Modifier,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val couleurs = MaterialTheme.colorScheme
    val trait = couleurs.primary
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val x = AXE.toPx()
                val y = HAUT.toPx() + styleTitre.lineHeight.toPx() / 2
                if (traitAuDessus) troncon(x, 0f, if (etatMarqueur == null) size.height else y, trait)
                if (traitAuDessous) troncon(x, y, size.height, trait)
                if (etatMarqueur != null) {
                    marqueur(
                        Offset(x, y), etatMarqueur, rond = true, couleurLigne = trait, encre = couleurs.onSurface,
                        fondMarqueur = couleurs.surfaceContainerLowest, fond = couleurs.surface, pointille = couleurs.outline,
                    )
                }
            }
            .semantics(mergeDescendants = true) { stateDescription = etatLu }
            .padding(start = DEBUT_TEXTE, end = 16.dp, top = HAUT, bottom = 16.dp),
        content = contenu,
    )
}

private fun SegmentEcran.etatLu(base: String) = if (actif) base else "$base, coupée"

/** Le dernier segment fait, réduit à une ligne ; le toucher y revient. */
@Composable
private fun RangeeFaite(segment: SegmentEcran, traitAuDessus: Boolean, actions: ActionsSeance) {
    val style = MaterialTheme.typography.bodyMedium
    RangeeSchema(
        EtatEtape.FAITE, style, traitAuDessus, traitAuDessous = true, segment.etatLu("Fait"),
        Modifier.heightIn(min = 56.dp).clickable(onClickLabel = "Revenir à cette étape", role = Role.Button, onClick = actions.precedent),
    ) {
        Text(segment.texte, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Le segment en cours, en grand sous le marqueur cerclé : 32 sp pour une action courte (les
 * segments de Cowork), 24 sp pour une étape entière du site, qui sinon remplirait l'écran et
 * cacherait la suite.
 */
@Composable
private fun RangeeCourante(segment: SegmentEcran, traitAuDessus: Boolean, traitAuDessous: Boolean, actions: ActionsSeance) {
    val typo = MaterialTheme.typography
    val style = if (segment.texte.length <= TEXTE_COURT) typo.headlineLarge else typo.headlineSmall
    RangeeSchema(EtatEtape.EN_COURS, style, traitAuDessus, traitAuDessous, segment.etatLu("En cours"), Modifier.heightIn(min = 96.dp)) {
        Text(
            segment.texte,
            style = style,
            color = if (segment.actif) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        // Couper le segment en cours, c'est la pause : seul un segment déjà coupé se réactive ici.
        if (!segment.actif) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MentionCoupee(Modifier.weight(1f))
                BoutonLecture(segment, actions.basculer)
            }
        }
    }
}

/** Au-delà, le segment en cours passe de 32 à 24 sp. */
private const val TEXTE_COURT = 90

/** Un segment à venir : son texte, quand il sera lu, et s'il le sera. */
@Composable
private fun RangeeAVenir(segment: SegmentEcran, traitAuDessous: Boolean, actions: ActionsSeance) {
    val style = MaterialTheme.typography.bodyLarge
    RangeeSchema(EtatEtape.A_FAIRE, style, traitAuDessus = true, traitAuDessous, segment.etatLu("À faire"), Modifier.heightIn(min = 72.dp)) {
        Text(
            segment.texte,
            style = style,
            color = if (segment.actif) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!segment.actif) MentionCoupee(Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoixDeclencheur(segment, actions, Modifier.weight(1f))
            BoutonLecture(segment, actions.basculer)
        }
    }
}

/** Les segments après les deux suivants : le trait file, sans marqueur. */
@Composable
private fun RangeeReste(restants: Int) {
    val style = MaterialTheme.typography.bodyMedium
    val texte = if (restants == 1) "Puis une dernière étape" else "Puis $restants autres étapes"
    RangeeSchema(null, style, traitAuDessus = true, traitAuDessous = false, etatLu = "À faire", Modifier.heightIn(min = 48.dp)) {
        Text(texte, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MentionCoupee(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_volume_off), contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("Coupée : pas lue à voix haute", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Couper ou réactiver la lecture d'un segment : un interrupteur, lu comme tel par TalkBack. */
@Composable
private fun BoutonLecture(segment: SegmentEcran, basculer: (String) -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(
        checked = segment.actif,
        onCheckedChange = { basculer(segment.id) },
        modifier = modifier.semantics { contentDescription = "Lire l'étape ${segment.numero} à voix haute" },
    ) {
        Icon(
            painterResource(if (segment.actif) R.drawable.ic_volume_up else R.drawable.ic_volume_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Quand le segment sera lu : un petit menu à trois choix. */
@Composable
private fun ChoixDeclencheur(segment: SegmentEcran, actions: ActionsSeance, modifier: Modifier = Modifier) {
    var ouvert by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Choisir quand l'étape ${segment.numero} sera lue", role = Role.Button) { ouvert = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(segment.declencheur.libelle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            Declencheur.entries.forEach { choix ->
                DropdownMenuItem(
                    text = { Text(choix.libelle, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        ouvert = false
                        actions.changerDeclencheur(segment.id, choix)
                    },
                    leadingIcon = {
                        if (choix == segment.declencheur) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = "Choisi")
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

private val Declencheur.libelle: String
    get() = when (this) {
        Declencheur.MANUEL -> "Lue sur demande"
        Declencheur.FIN_TACHE_PRECEDENTE -> "Lue après l'étape d'avant"
        Declencheur.FIN_MINUTEUR -> "Lue à la fin du minuteur"
    }

// ── Le dock ─────────────────────────────────────────────────────────────────────

@Composable
private fun Dock(etat: EtatSeance, actions: ActionsSeance) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (etat.micro == EtatEcoute.Ecoute) LigneMicro(etat.entendu)
            etat.autresMinuteurs.forEach { AutreMinuteur(it, actions) }
            // « Lire » seulement si la voix peut parler : sinon, un bouton qui ne fait rien.
            val voixDisponible = !etat.voixCoupee && (etat.voix == EtatVoix.PRETE || etat.voix == EtatVoix.PREPARATION)
            val lire = etat.courant?.actif == true && voixDisponible
            if (lire || etat.minuteur != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (lire) {
                        BoutonCarnet(
                            if (etat.dejaDit) "Relire" else "Lire",
                            actions.relire,
                            Modifier.weight(1f).heightIn(min = 72.dp),
                            sorte = SorteBouton.CONTOUR,
                            icone = if (etat.dejaDit) R.drawable.ic_replay else R.drawable.ic_play_arrow,
                        )
                    }
                    etat.minuteur?.let { BoutonMinuteur(it, actions, Modifier.weight(1f)) }
                }
            }
            BoutonCarnet("C'est fait", actions.valider, Modifier.fillMaxWidth().heightIn(min = 72.dp), icone = R.drawable.ic_check)
        }
    }
}

/**
 * Le témoin du micro ouvert, discret : ce qu'on peut dire, puis, une fois qu'il a entendu
 * quelque chose, ce qu'il a compris à la place (une ligne de moins dans le dock).
 */
@Composable
private fun LigneMicro(entendu: Entendu?) {
    val couleurs = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_mic), contentDescription = null, Modifier.size(24.dp), tint = couleurs.primary)
        Spacer(Modifier.width(12.dp))
        when (entendu) {
            null -> Text("Micro ouvert : dis « suivant », « répète » ou « pause ».", style = style, color = couleurs.onSurface)
            is Entendu.Compris -> Text(
                "Micro ouvert. Compris : « ${entendu.commande.libelle} »",
                style = style,
                fontWeight = FontWeight.Bold,
                color = couleurs.onSurface,
            )
            is Entendu.EnCours -> Text(
                "Micro ouvert. J'entends : « ${entendu.texte} »",
                style = style,
                color = couleurs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            is Entendu.PasCompris -> Text(
                "Micro ouvert. Pas une commande : « ${entendu.texte} »",
                style = style,
                color = couleurs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Le minuteur du dock : sa durée et « Lancer », ou le temps restant et « Arrêter ». */
@Composable
private fun BoutonMinuteur(minuteur: MinuteurEcran, actions: ActionsSeance, modifier: Modifier = Modifier) {
    val couleurs = MaterialTheme.colorScheme
    val description = if (minuteur.enMarche) {
        "Minuteur de l'étape ${minuteur.numero} : ${dureeDite(minuteur.secondes)} restantes. Arrêter le minuteur."
    } else {
        "Lancer le minuteur de ${dureeDite(minuteur.secondes)}"
    }
    OutlinedButton(
        onClick = { actions.minuteur(minuteur) },
        modifier = modifier.heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (minuteur.enMarche) {
            ButtonDefaults.outlinedButtonColors(containerColor = couleurs.secondaryContainer, contentColor = couleurs.onSecondaryContainer)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(duree(minuteur.secondes), style = MaterialTheme.typography.headlineSmall.chiffres())
                Text(if (minuteur.enMarche) "Arrêter" else "Lancer", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Un autre minuteur qui tourne encore, d'une étape déjà validée. */
@Composable
private fun AutreMinuteur(minuteur: MinuteurEcran, actions: ActionsSeance) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_timer), contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            "Étape ${minuteur.numero} : ${duree(minuteur.secondes)}",
            style = MaterialTheme.typography.bodyLarge.chiffres(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = "Minuteur de l'étape ${minuteur.numero} : ${dureeDite(minuteur.secondes)} restantes"
            },
        )
        BoutonCarnet("Arrêter", { actions.minuteur(minuteur) }, sorte = SorteBouton.TEXTE)
    }
}

// ── La fin ──────────────────────────────────────────────────────────────────────

@Composable
private fun FinDeSeance(etat: EtatSeance, actions: ActionsSeance, modifier: Modifier = Modifier) {
    val enMarche = listOfNotNull(etat.minuteur?.takeIf { it.enMarche }) + etat.autresMinuteurs
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
        LigneDeTemps(listOf(Etape("Recette terminée", EtatEtape.FAITE, detail = "Toutes les étapes sont faites. Bon appétit !")))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            enMarche.forEach { AutreMinuteur(it, actions) }
            BoutonCarnet("Revenir", actions.revenir, Modifier.fillMaxWidth().heightIn(min = 72.dp), icone = R.drawable.ic_arrow_back)
            BoutonCarnet("Revoir la dernière étape", actions.precedent, Modifier.fillMaxWidth(), sorte = SorteBouton.TEXTE, icone = R.drawable.ic_skip_previous)
        }
    }
}

// ── Durées ──────────────────────────────────────────────────────────────────────

/** « 5:00 », « 1:05:00 » : à lire d'un coup d'œil. */
internal fun duree(secondes: Int): String {
    val h = secondes / 3600
    val m = secondes % 3600 / 60
    val s = (secondes % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}

/** « 4 minutes 59 », « 45 secondes » : ce que TalkBack lit. */
internal fun dureeDite(secondes: Int): String {
    val m = secondes / 60
    val s = secondes % 60
    return when {
        m == 0 -> if (s <= 1) "$s seconde" else "$s secondes"
        s == 0 -> if (m == 1) "1 minute" else "$m minutes"
        else -> "${if (m == 1) "1 minute" else "$m minutes"} $s"
    }
}
