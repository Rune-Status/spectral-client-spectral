package org.spectral.mapper.execution

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue

/**
 * Represents an ASM analyzer which builds a control flow frame graph
 * as well as a instruction context of the stack at each frame.
 *
 * @property group MutableList<ClassNode>
 * @constructor
 */
class BlockAnalyzer : Analyzer<BasicValue>(BasicInterpreter()) {

    /**
     * A list of the execution frames analyzed by this analyzer.
     */
    val blocks = mutableListOf<Block>()

    override fun init(owner: String, method: MethodNode) {
        val instructions = method.instructions
        var currentBlock = Block()

        blocks.add(currentBlock)

        for(i in 0 until instructions.size()) {
            val insn = instructions[i]
            currentBlock.endIndex++

            if(i >= currentBlock.startIndex && i <= currentBlock.endIndex) {
                currentBlock.instructions.add(instructions[i])
            }

            if(insn.next == null) break
            if(insn.next.type == AbstractInsnNode.LABEL ||
                insn.type == AbstractInsnNode.JUMP_INSN ||
                insn.type == AbstractInsnNode.LOOKUPSWITCH_INSN ||
                insn.type == AbstractInsnNode.TABLESWITCH_INSN
            ) {
                currentBlock = Block()
                currentBlock.startIndex = i + 1
                currentBlock.endIndex = i + 1

                blocks.add(currentBlock)
            }
        }
    }

    override fun newControlFlowEdge(insnIndex: Int, successorIndex: Int) {
        val currentBlock = findBlock(insnIndex)
        val nextBlock = findBlock(successorIndex)

        if(currentBlock != nextBlock) {
            if(insnIndex + 1 == successorIndex) {
                currentBlock.next = nextBlock
                nextBlock.prev = currentBlock
            } else {
                currentBlock.branches.add(nextBlock)
            }
        }
    }

    private fun findBlock(index: Int): Block {
        return blocks.first { index in it.startIndex until it.endIndex }
    }
}