package guru.nidi.dicamo

import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

val log = LoggerFactory.getLogger("grammar")

private val VERB_TYPES = listOf(
    "ar", "car", "iar", "jar", "uar",
    "er",
    "re", "bre", "dre", "ndre", "ur", "ure",
    "ir", "gir", "rir"
)

private class FlatEnding(val possibleBase: (String) -> Boolean, val groups: Map<String, Set<String>>)

private val effectiveEndings: Map<String, FlatEnding> =
    verbs.entries.associate { (infEnding, ending) ->
        infEnding to FlatEnding(ending.possibleBase, ending.groups
            .flatMap { (name, group) -> name.split(",").map { it to group } }
            .associate { (name, group) ->
                val addForDefault = if ("/" in name) name.substring(name.indexOf("/") * 2 + 1) else ""
                name to ending.defaultGroup.flatMap { (form, defaultForms) ->
                    val forms = group[form] ?: listOf()
                    (defaultForms.indices union forms.indices).map { index ->
                        ((forms.getOrNull(index) ?: (addForDefault + defaultForms[index]))).normalize()
                    }
                }.toSet()
            })
    }

private infix fun IntRange.union(other: IntRange) = IntRange(min(first, other.first), max(last, other.last))

private fun baseInGroup(base: String, ending: String, group: String): String? {
    val type = VerbList.verbs[base + ending]?.type ?: Type.UNKNOWN
    val fromBase = group.substringBefore("/")
    val toBase = group.substringAfter("/")
    return when {
        group == "" -> if (type != Type.INCOATIU) base else null
        group == "incoatiu" -> if (type != Type.PUR) base else null
        fromBase == base -> base.dropLast(fromBase.length) + toBase
        fromBase.firstOrNull() == '-' && base.endsWith(fromBase.drop(1)) ->
            base.dropLast(fromBase.length - 1) + toBase.drop(1)
        else -> null
    }
}

fun baseOf(word: String): Collection<String> =
    (singularNounsOf(word).flatMap { masculinNounsOf(it) } +
            singularAdjectivesOf(word)
                .flatMap { masculinAdjectivesOf(it) }
                .flatMap { baseDegreeAdjectivesOf(it) })
        .toSet()

fun baseDegreeAdjectivesOf(word: String): List<String> {
    return when (word) {
        "millor", "optim" -> listOf("bo", "bon")
        "pitjor", "pessim" -> listOf("mal", "dolent")
        "major", "maxim" -> listOf("gran")
        "menor", "minim" -> listOf("petit")
        "superior", "suprem" -> listOf("alt")
        "inferior", "infim" -> listOf("baix")
        else -> listOf(if (word.endsWith("issim")) word.dropLast(5) else word)
    }
}

fun masculinAdjectivesOf(word: String): List<String> {
    val res = if (!word.endsWith("a")) listOf(word)
    else word.replaceEnd("a", "") +
            word.replaceEnd("a", "e") +
            word.replaceEnd("a", "o") +
            word.replaceEnd("da", "t") +
            word.replaceEnd("ga", "c") +
            word.replaceEnd("qua", "c") +
            word.replaceEnd("ja") {
                listOf(
                    when {
                        it.endsWith("it") -> it.dropLast(2) + "ig"
                        it.endsWith("t") -> it.dropLast(1) + "ig"
                        else -> it + "ig"
                    }
                )
            } +
            word.replaceEnd("ssa", "s") +
            word.replaceEnd("na", "") +
            word.replaceEnd("ea", "eu") +
            word.replaceEnd("ava", "au") +
            word.replaceEnd("eva", "eu") +
            word.replaceEnd("iva", "iu") +
            word.replaceEnd("ova", "ou") +
            word.replaceEnd("lla", "l")
    log.debug("Masculins of '$word': $res")
    return res
}

fun singularAdjectivesOf(word: String): List<String> {
    val res = if (!word.endsWith("s")) listOf(word)
    else word.replaceEnd("s", "") +
            word.replaceEnd("sos", "s") +
            word.replaceEnd("xos", "x") +
            word.replaceEnd("scos", "sc") +
            word.replaceEnd("stos", "st") +
            word.replaceEnd("xtos", "xt") +
            word.replaceEnd("ns") { if (it.last() in "aeiou") listOf(it) else listOf() } +
            word.replaceEnd("ssos", "s") +
            word.replaceEnd("jos", "ig") +
            word.replaceEnd("itjos", "ig") +
            word.replaceEnd("es") { it.frontToBack().map { it + "a" } }
    log.debug("Singulars of '$word': $res")
    return res
}

fun masculinNounsOf(word: String): List<String> {
    val res = if (!word.endsWith("a")) listOf(word)
    else word.replaceEnd("a", "") +
            word.replaceEnd("da", "t") +
            word.replaceEnd("ba", "p") +
            word.replaceEnd("va", "f") +
            word.replaceEnd("ssa", "s") +
            word.replaceEnd("na", "") +
            word.replaceEnd("essa", "")
    log.debug("Masculins of '$word': $res")
    return res
}

fun singularNounsOf(word: String): List<String> {
    val res = if (!word.endsWith("s")) listOf(word)
    else word.replaceEnd("s", "") +
            word.replaceEnd("sos", "s") +
            word.replaceEnd("xos", "x") +
            word.replaceEnd("scos", "sc") +
            word.replaceEnd("stos", "st") +
            word.replaceEnd("xtos", "xt") +
            word.replaceEnd("ns") { if (it.last() in "aeiou") listOf(it) else listOf() } +
            word.replaceEnd("ssos", "s") +
            word.replaceEnd("jos", "ig") +
            word.replaceEnd("itjos", "ig") +
            word.replaceEnd("es") { it.frontToBack().map { it + "a" } }
    log.debug("Singulars of '$word': $res")
    return res
}

fun infinitivesOf(word: String): Pair<List<String>, List<String>> {
    val pronounLess = word.substringBeforeLast('-').substringBeforeLast('\'')
    val baseInfs = baseInfinitivesOf(pronounLess)
    val infs = baseInfs.mapNotNull { VerbList.verbs[it]?.name }
    log.debug("Infinitives of '$word': $infs / $baseInfs")
    return Pair(infs, baseInfs.flatMap { addDiacritics(it) })
}

private fun baseInfinitivesOf(word: String): List<String> {
    val infs = effectiveEndings.flatMap { (infEnding, ending) ->
        ending.groups.flatMap { (name, group) ->
            group
                .filter { ending -> word.endsWith(ending) }
                .maxByOrNull { ending -> ending.length }
                ?.let { longestEnding ->
                    val base = baseInGroup(word.dropLast(longestEnding.length), infEnding, name)
                    if (base == null || !ending.possibleBase(base)) null
                    else base.replaceEnding(longestEnding, infEnding)
                }
                ?.filterNot { inf -> inf in VERB_TYPES }
                ?: listOf()
        }.toSet()
    }
    return infs
}

val diacritics = listOf(
    "aixer" to "àixer", "anyer" to "ànyer",
    "coneixer" to "conèixer", "creixer" to "créixer", "reixer" to "rèixer", "neixer" to "néixer", "peixer" to "péixer",
    "enyer" to "ènyer", "encer" to "èncer", "emer" to "émer",
    "aitzar" to "aïtzar", "eitzar" to "eïtzar",
    "orrer" to "órrer", "orcer" to "òrcer", "omer" to "òmer",
    "umer" to "úmer", "unyer" to "únyer",
)

private fun addDiacritics(s: String): List<String> {
    // ï and ç are not completely handled
    diacritics
        .firstOrNull { s.endsWith(it.first) }
        ?.let { return listOf(s.dropLast(it.first.length) + it.second) }
    if (s.endsWith("car")) return listOf(s, s.dropLast(3) + "çar")
    return listOf(s)
}

internal fun String.replaceEnding(oldEnding: String, newEnding: String): List<String> {
    val newBase = when (newEnding) {
        "ar" -> when {
            oldEnding.startsWithFront() -> frontToBack()
            else -> listOf(this)
        }
        "ir", "er" -> when {
            oldEnding.startsWithBack() -> backToFront()
            else -> listOf(this)
        }
        else -> listOf(this)
    }
    return newBase.map { it + newEnding }
}
