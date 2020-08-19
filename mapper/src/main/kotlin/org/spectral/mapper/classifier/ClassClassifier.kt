package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
import org.spectral.asm.Class
import org.spectral.asm.Method
import org.spectral.mapper.AbstractClassifier
import org.spectral.mapper.ClassifierUtil
import org.spectral.mapper.RankResult
import kotlin.math.max
import kotlin.math.pow

/**
 * Responsible for running classifiers on [Class] objects and calculating
 * a similarity match score.
 */
object ClassClassifier : AbstractClassifier<Class>() {

    /**
     * Initialize / register the classifiers.
     */
    override fun init() {
        addClassifier(classTypeCheck, 20)
        addClassifier(hierarchyDepth, 1)
        addClassifier(parentClass, 4)
        addClassifier(childClasses, 3)
        addClassifier(interfaces, 3)
        addClassifier(implementers, 2)
        addClassifier(methodCount, 3)
        addClassifier(fieldCount, 3)
        addClassifier(hierarchySiblings, 2)
        addClassifier(similarMethods, 10)
    }

    override fun rank(src: Class, dsts: Array<Class>): List<RankResult<Class>> {
        return ClassifierUtil.rank(src, dsts, classifiers, ClassifierUtil::isPotentiallyEqual, this.maxMismatch)
    }

    /**
     * Class Types
     */
    private val classTypeCheck = classifier("class type check") { a, b ->
        val mask = ACC_ENUM or ACC_INTERFACE or ACC_ANNOTATION or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 4).toDouble()
    }

    /**
     * Hierarchy Depth
     */
    private val hierarchyDepth = classifier("hierarchy depth") { a, b ->
        return@classifier ClassifierUtil.compareCounts(a.hierarchy.size, b.hierarchy.size)
    }

    /**
     * Hierarchy Siblings
     */
    private val hierarchySiblings = classifier("hierarchy siblings") { a, b ->
        return@classifier ClassifierUtil.compareCounts(a.parent!!.children.size, b.parent!!.children.size)
    }

    /**
     * Parent Class
     */
    private val parentClass = classifier("parent class") { a, b ->
        if(a.parent == null && b.parent == null) return@classifier 1.0
        if(a.parent == null || b.parent == null) return@classifier 0.0

        return@classifier if(ClassifierUtil.isPotentiallyEqual(a.parent!!, b.parent!!)) 1.0 else 0.0
    }

    /**
     * Child Classes
     */
    private val childClasses = classifier("child classes") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(a.children, b.children)
    }

    /**
     * Interfaces
     */
    private val interfaces = classifier("interfaces") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(a.interfaces, b.interfaces)
    }

    /**
     * Implementers
     */
    private val implementers = classifier("implementers") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(a.implementers, b.implementers)
    }

    /**
     * Method Count
     */
    private val methodCount = classifier("method count") { a, b ->
        return@classifier ClassifierUtil.compareCounts(a.methods.size, b.methods.size)
    }

    /**
     * Field Count
     */
    private val fieldCount = classifier("field count") { a, b ->
        return@classifier ClassifierUtil.compareCounts(a.fields.size, b.fields.size)
    }

    /**
     * Similar Methods
     */
    private val similarMethods = classifier("similar methods") { a, b ->
        if(a.methods.isEmpty() && b.methods.isEmpty()) return@classifier 1.0
        if(a.methods.isEmpty() || b.methods.isEmpty()) return@classifier 0.0

        val methodsB = hashSetOf<Method>().apply { this.addAll(b.methods) }
        var totalScore = 0.0
        var bestMatch: Method? = null
        var bestScore = 0.0

        for(methodA in a.methods) {
            loopB@ for(methodB in methodsB) {
                if(!ClassifierUtil.isPotentiallyEqual(methodA, methodB)) continue
                if(!ClassifierUtil.isReturnTypesPotentiallyEqual(methodA, methodB)) continue
                if(!ClassifierUtil.isArgTypesPotentiallyEqual(methodA, methodB)) continue@loopB

                val score: Double = if(methodA.real || methodB.real) {
                    if(methodA.real && methodB.real) 1.0 else 0.0
                } else {
                    ClassifierUtil.compareCounts(methodA.instructions.size(), methodB.instructions.size())
                }

                if(score > bestScore) {
                    bestScore = score
                    bestMatch = methodB
                }
            }

            if(bestMatch != null) {
                totalScore += bestScore
                methodsB.remove(bestMatch)
            }
        }

        return@classifier totalScore / max(a.methods.size, b.methods.size)
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}