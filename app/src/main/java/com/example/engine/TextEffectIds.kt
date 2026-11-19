package com.example.engine

/**
 * The effect ids of the text animators the text engine evaluates natively.
 *
 * These ids are stored on [com.example.model.TimelineClip.textAnimators] and are how
 * [TextAnimEval], [TextProgressEval] and [CounterEval] recognise their animator on a clip.
 * They are Timeline Pro's own identities now: the text editor creates the reveal animator
 * ([TEXTPROGRESS]) itself, and the Pie Counter / Social Growth templates author [COUNTER]
 * animators directly. (The id strings are historical — they began as Alight Motion's effect
 * ids, back when the XML importer introduced these animators; the values must never change,
 * because every saved project refers to its animators by them.)
 */
object TextEffectIds {
    const val TEXTTRANSFORM = "com.alightcreative.effects.texttransform"
    const val RANDOMDISPLACE = "com.alightcreative.effects.randomdisplace"
    const val TEXTPROGRESS = "com.alightcreative.effects.textprogress"
    const val COUNTER = "com.alightcreative.effects.counter"
}
