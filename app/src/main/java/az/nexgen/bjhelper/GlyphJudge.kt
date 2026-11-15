package az.nexgen.bjhelper

/** Decides what a classified glyph means for the reading. */
object GlyphJudge {

    enum class Verdict { RANK, JUNK, UNSURE }

    const val MIN_CONF = 0.6f

    fun judge(r: RankClassifier.Result): Verdict {
        if (r.confidence < MIN_CONF) return Verdict.UNSURE
        // a clearly "not a rank" mark (card edge, shadow, pip piece) is ignored, not a reason to wait
        return if (r.rank == null) Verdict.JUNK else Verdict.RANK
    }
}
