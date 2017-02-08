package com.suhininalex.clones.core

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.ElementType.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtil
import com.suhininalex.clones.ide.ProjectClonesInitializer
import com.suhininalex.suffixtree.Edge
import com.suhininalex.suffixtree.Node
import java.awt.EventQueue
import java.lang.IllegalArgumentException
import java.util.*

val PsiMethod.stringId: String
    get() =
        containingFile.containingDirectory.name + "." +
        containingClass!!.name + "." +
        name + "." +
        parameterList;

val Edge.length: Int
    get() = end - begin + 1

fun Clone.getTextRangeInMethod(offset: Int) = TextRange(firstElement.getTextRange().startOffset - offset, lastElement.getTextRange().endOffset-offset)

fun Project.getCloneManager() = ProjectClonesInitializer.getInstance(this)

fun Token.getTextRange() = source.textRange

fun Node.lengthToRoot() =
    riseTraverser().sumBy { it.parentEdge?.length ?: 0 }

fun <T> callInEventQueue(body: ()->T): T {
    if (EventQueue.isDispatchThread()) return body()
    var result: T? = null
    EventQueue.invokeAndWait { result = body() }
    return result!!
}

val Application: Application
    get() = ApplicationManager.getApplication()


fun Node.riseTraverser() = object: Iterable<Node> {
    var node: Node? = this@riseTraverser
    override fun iterator() = iterate {
        val result = node
        node = node?.parentEdge?.parent
        result
    }
}

fun Node.descTraverser() = riseTraverser().reversed()

fun Project.getAllPsiJavaFiles() =
    PsiManager.getInstance(this).findDirectory(baseDir)!!.getPsiJavaFiles()

fun PsiDirectory.getPsiJavaFiles(): Sequence<PsiJavaFile> =
    this.depthFirstTraverse { it.subdirectories.asSequence() }.flatMap { it.files.asSequence() }.filterIsInstance<PsiJavaFile>()

fun PsiElement.findTokens(filter: TokenSet): Sequence<PsiElement> =
    this.leafTraverse({it in filter}) {it.children.asSequence()}

operator fun TokenSet.contains(element: PsiElement?): Boolean = this.contains(element?.node?.elementType)

fun PsiElement.asSequence(): Sequence<PsiElement> =
    this.depthFirstTraverse { it.children.asSequence() }.filter { it.firstChild == null }

fun CloneClass.tokenSequence(): Sequence<Token> =
        treeNode.descTraverser().asSequence().map { it.parentEdge }.filter { it != null }.flatMap(Edge::asSequence)

@Suppress("UNCHECKED_CAST")
fun Edge.asSequence(): Sequence<Token> {
    if (isTerminal) {
        throw IllegalArgumentException("You should never call this method for terminating edge.")
    } else {
        return (sequence.subList(begin, end + 1) as MutableList<Token>).asSequence()
    }
}

val javaTokenFilter = TokenSet.create(
        WHITE_SPACE, DOC_COMMENT, C_STYLE_COMMENT, END_OF_LINE_COMMENT//, SEMICOLON, CODE_BLOCK, RPARENTH, LPARENTH, RBRACE, LBRACE,  EXPRESSION_LIST
)

val Edge.isTerminal: Boolean
    get() = this.terminal == null

fun <T> T.depthFirstTraverse(children: (T) -> Sequence<T>): Sequence<T> =
        sequenceOf(this) + children(this).flatMap { it.depthFirstTraverse(children) }

fun <T> T.depthFirstTraverse(recursionFilter: (T)-> Boolean, children: (T) -> Sequence<T>) =
        this.depthFirstTraverse { if (recursionFilter(it)) children(it) else emptySequence() }

fun <T> T.leafTraverse(isLeaf: (T)-> Boolean, children: (T) -> Sequence<T>) =
        this.depthFirstTraverse ({ ! isLeaf(it) }, children).filter { isLeaf(it) }

fun <T> times(times: Int, provider: ()-> Sequence<T>): Sequence<T> =
    (1..times).asSequence().flatMap { provider() }

infix fun <T> Sequence<T>.equalContent(another: Sequence<T>) =
    zip(another).all { (a,b) -> a == b }

fun <T> Sequence<T>.isEmpty() =
        iterator().hasNext()

inline fun <E> MutableList<E>.addIf(element: E, condition:(element: E)->Boolean){
    if (condition(element)) add(element)
}

fun <T> iterate(f:()->T?) = object : Iterator<T>{
    var next :T? = f()
    override fun hasNext() = next!=null
    override fun next():T {
        val result = next ?: throw NoSuchElementException()
        next = f()
        return result
    }
}

fun String.abbreviate(length: Int) =
    "${take(length)}..."

fun <T> Sequence<Sequence<T>>.zipped(): List<List<T>>{
    val result = ArrayList<ArrayList<T>>()
    forEach {
        it.forEachIndexed { i, it ->
            if (i >= result.size) result.add(ArrayList<T>())
            result[i].add(it)
        }
    }
    return result
}