import java.io.File
import kotlin.collections.ArrayDeque
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.engine.Format

// ---------------------------------------------------------
// Laboratorio 2
// ---------------------------------------------------------
// Lista completa de operadores
val ALL_OPERATORS = setOf('|', '.', '?', '*', '+', '^')
// Operadores que actuan sobre 1 simbolo
val UNARY_OPERATORS = setOf('*', '+', '?')
// Operadores que actuan sobre 2 simbolos
val BINARY_OPERATORS = setOf('|', '^', '.')

// Precedencia de operadores
fun getPrecedence(operatorChar: Char): Int {
    return when (operatorChar) {
        '(' -> 1
        '|' -> 2
        '.' -> 3
        '?', '*', '+' -> 4
        '^' -> 5
        else -> 0
    }
}

// Manejar clases
// Metodo para retornar una clase tal cual como viene
// y saber el indice en el que seguimos inmediatamente despues del ]
fun readClass(pattern: String, startIndex: Int): Pair<String, Int> {
    val sb = StringBuilder()
    var i = startIndex
    sb.append('[')
    i += 1
    while (i < pattern.length) {
        val ch = pattern[i]
        if (ch == '\\' && i + 1 < pattern.length) {
            sb.append('\\').append(pattern[i + 1])
            i += 2
            continue
        }
        sb.append(ch)
        i += 1
        if (ch == ']') break
    }
    return sb.toString() to i
}

// Formateo de la regex 
// Insertar concatenaciones entre simbolos con el '.'
// Mantiene clases y escape characters como unidades
fun formatRegex(rawRegex: String): String {
    val formattedRegexBuilder = StringBuilder()

    var index = 0

    while (index < rawRegex.length) {
        val currentChar = rawRegex[index]

        if (currentChar == '[') {
            val (charClass, classEndIndex) = readClass(rawRegex, index)
            formattedRegexBuilder.append(charClass)

            if (classEndIndex < rawRegex.length) {
                val nextChar = rawRegex[classEndIndex]
                val concatNecesaria =
                    (nextChar != ')') &&
                    (nextChar !in ALL_OPERATORS) &&
                    (nextChar != '\\') &&
                    (nextChar != ']')
                if (concatNecesaria) {
                    formattedRegexBuilder.append('.')
                }
            }
            index = classEndIndex
            continue
        }

        if (currentChar == '\\' && index + 1 < rawRegex.length) {
            formattedRegexBuilder.append('\\')
            formattedRegexBuilder.append(rawRegex[index + 1])

            // Posible concatenación tras el escape
            if (index + 2 < rawRegex.length) {
                val nextChar = rawRegex[index + 2]
                if (nextChar != ')' && nextChar !in ALL_OPERATORS && nextChar != '\\') {
                    formattedRegexBuilder.append('.')
                }
            }
            index += 2
            continue
        }

        if (currentChar == '.') {
            // Normalizamos a '\.' como token literal
            formattedRegexBuilder.append('\\').append('.')
            if (index + 1 < rawRegex.length) {
                val nextChar = rawRegex[index + 1]
                val concatNecesaria =
                    (nextChar != ')') &&
                    (nextChar !in ALL_OPERATORS) &&
                    (nextChar != '\\')
                if (concatNecesaria) {
                    println("Inserto concatenación '.' entre '$currentChar' y '$nextChar'")
                    formattedRegexBuilder.append('.')
                }
            }
            index += 1
            continue
        }

        formattedRegexBuilder.append(currentChar)

        if (index + 1 < rawRegex.length) {
            val nextChar = rawRegex[index + 1]
            val concatNecesaria =
                (currentChar != '(') &&
                (nextChar != ')') &&
                (nextChar !in ALL_OPERATORS) &&
                (currentChar !in BINARY_OPERATORS) ||
                (currentChar in UNARY_OPERATORS && (nextChar == '(' || nextChar == '\\' || (nextChar !in ALL_OPERATORS && nextChar != ')')))
            if (concatNecesaria) {
                println("Inserto concatenación '.' entre '$currentChar' y '$nextChar'")
                formattedRegexBuilder.append('.')
            }
        }
        index++
    }
    return formattedRegexBuilder.toString()
}

// Conversión de regex infija a posfija
// Shunting yard
fun infixToPostfix(regex: String): String {
    val postfixBuilder = StringBuilder()
    val operatorStack = ArrayDeque<Char>()

    // Colocar concatenacion explicita
    val formattedRegex = formatRegex(regex)

    var index = 0

    while (index < formattedRegex.length) {
        val currentChar = formattedRegex[index]

        // Manejar como un solo token si es clase
        if (currentChar == '[') {
            val (charClass, j) = readClass(formattedRegex, index)
            postfixBuilder.append(charClass)
            index = j
            continue
        }

        // los \ se van directos al output
        if (currentChar == '\\' && index + 1 < formattedRegex.length) {
            postfixBuilder.append('\\').append(formattedRegex[index + 1])
            index += 2
            continue
        }

        // Unarios postfix emitir directo al output, asi dice el pseudocodigo
        if (currentChar in UNARY_OPERATORS) {
            postfixBuilder.append(currentChar)
            index++
            continue
        }

        when {
            // parentesis de apertura directo a la pila
            currentChar == '(' -> {
                operatorStack.addFirst(currentChar)
                index++
            }
            // Antes de apilarlo, se saca de la pila cualquier operador que este adentro
            //que tenga mayor o igual precedencia que el actual
            currentChar in ALL_OPERATORS -> {
                while (operatorStack.isNotEmpty() && operatorStack.first() != '(' && getPrecedence(operatorStack.first()) >= getPrecedence(currentChar)) {
                    val operator = operatorStack.removeFirst()
                    postfixBuilder.append(operator)
                }
                operatorStack.addFirst(currentChar)
                index++
            }
            // Se desapila hasta encontrar un '('
            currentChar == ')' -> {
                while (operatorStack.isNotEmpty() && operatorStack.first() != '(') {
                    val operator = operatorStack.removeFirst()
                    postfixBuilder.append(operator)
                }
                if (operatorStack.isNotEmpty() && operatorStack.first() == '(' ) {
                    operatorStack.removeFirst() // descartar (
                }
                index++
            }
            else -> {
                // Simbolo
                postfixBuilder.append(currentChar)
                i++
            }
        }
    }

    while (operatorStack.isNotEmpty()) {
        val topOperator = operatorStack.removeFirst()
        if (topOperator == '(') {
            throw IllegalArgumentException("Paréntesis desbalanceados: falta ')'")
        }
        postfixBuilder.append(topOperator)
    }

    return postfixBuilder.toString()
}
// ---------------------------------------------------------------

// Laboratorio 3 -------------------------------------------------
// Sustituciones de Operadores
// Pasar de X? a algo como (X|ε)
fun sustituirOpcional(pattern: String): String {
    val outputBuilder = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val currentChar = pattern[index]
        if (currentChar == '?') {
            // Obtener el operando inmediatamente anterior
            val builtSoFar = outputBuilder.toString()
            var start = builtSoFar.length - 1
            val operand = when {
                // Grupo entre paréntesis
                builtSoFar[start] == ')' -> {
                    var depth = 1
                    start--
                    while (start >= 0 && depth > 0) {
                        if (builtSoFar[start] == ')') depth++
                        if (builtSoFar[start] == '(') depth--
                        start--
                    }
                    builtSoFar.substring(start + 1)
                }
                // Clase de caracteres 
                builtSoFar[start] == ']' -> {
                    start--
                    while (start >= 0 && builtSoFar[start] != '[') start--
                    builtSoFar.substring(start)
                }
                // Secuencia escapada
                start > 0 && builtSoFar[start - 1] == '\\' -> {
                    builtSoFar.substring(start - 1)
                }
                // Símbolo simple
                else -> {
                    builtSoFar.substring(start)
                }
            }
            // quitar el operando recién detectado
            val operandStartIndex = outputBuilder.length - operand.length
            outputBuilder.setLength(operandStartIndex)
            outputBuilder.append("(").append(operand).append("|ε)")
            index++
        } else {
            outputBuilder.append(currentChar)
            index++
        }
    }
    return outputBuilder.toString()
}


// Si encontramos un + como operador
// hay que trabajarlo de a+ a algo como aa*
fun sustituirMas(pattern: String): String {
    val outputBuilder = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val currentChar = pattern[index]
        if (currentChar == '+') {
            // Obtener el operando justo antes de '+'
            val builtSoFar = outputBuilder.toString()
            var start = builtSoFar.length - 1
            val operand = when {
                // Grupo entre paréntesis (...)
                builtSoFar[start] == ')' -> {
                    var depth = 1
                    start--
                    while (start >= 0 && depth > 0) {
                        if (builtSoFar[start] == ')') depth++
                        if (builtSoFar[start] == '(') depth--
                        start--
                    }
                    builtSoFar.substring(start + 1)
                }
                // Clase de caracteres [...]
                builtSoFar[start] == ']' -> {
                    start--
                    while (start >= 0 && builtSoFar[start] != '[') start--
                    builtSoFar.substring(start, builtSoFar.length)
                }
                // Secuencia escapada (\x)
                start > 0 && builtSoFar[start - 1] == '\\' -> builtSoFar.substring(start - 1)
                else -> builtSoFar.substring(start)
            }
            // Expandir operando+ a operando(operando)*
            outputBuilder.append('(')
            outputBuilder.append(operand)
            outputBuilder.append(')')
            outputBuilder.append('*')
            index++
        } else {
            outputBuilder.append(currentChar)
            index++
        }
    }
    return outputBuilder.toString()
}


// Nodo de Arbol --------------------------------------------------------------------------
data class TreeNode<T>(
  val value: T,
  val children: MutableList<TreeNode<T>> = mutableListOf()
) {
  fun addChild(node: TreeNode<T>) {
    children.add(node)
  }
}

// Tokeniza la salida postfix en una lista de tokens (clases, escapes y símbolos)
fun tokenize(postfixStr: String): List<String> {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < postfixStr.length) {
        when {
            postfixStr[index] == '[' -> {
                // Clase de caracteres
                val sb = StringBuilder().append('[')
                index++
                while (index < postfixStr.length) {
                    sb.append(postfixStr[index])
                    if (postfixStr[index] == ']') { index++; break }
                    index++
                }
                tokens.add(sb.toString())
            }
            postfixStr[index] == '\\' && index + 1 < postfixStr.length -> {
                // Escape
                tokens.add(postfixStr.substring(index, index + 2))
                index += 2
            }
            else -> {
                // Un solo carácter
                tokens.add(postfixStr[index].toString())
                index++
            }
        }
    }
    return tokens
}

// Generar DOT para el arbol -- TERMINAR DE ENTENER
fun generateDot(root: TreeNode<String>): String {
    val dotBuilder = StringBuilder()
    dotBuilder.appendLine("digraph AST {")
    dotBuilder.appendLine("  node [shape=circle];")
  
    // Genera nodos únicos
    fun visit(node: TreeNode<String>, nodeId: Int, generateNextId: () -> Int) {
      dotBuilder.appendLine("  n$nodeId [label=\"${node.value}\"];")
      for (child in node.children) {
        val childId = generateNextId()
        dotBuilder.appendLine("  n$nodeId -> n$childId;")
        visit(child, childId, generateNextId)
      }
    }
  
    var nodeIdCounter = 0
    visit(root, nodeIdCounter) { ++nodeIdCounter }
    dotBuilder.appendLine("}")
    return dotBuilder.toString()
}

fun renderDot(dotSource: String, outputFile: File) {
    Graphviz
      .fromString(dotSource)
      .render(Format.PNG)      
      .toFile(outputFile)      
}

// LABORATORIO 4 -------------------------------------------------
data class AFN_State(
    val id: Int,
    val prev_state: AFN_State[]? //nulo para cuando es el estado inicial
    val next_state: AFN_State[]? //nulo para cuando es el estado final
    val entry_symbol: Char? //nulo para cuando es el estado final
)

// Metodo para recorrer arbol como inorder
// visit se ejecuta en cada nodo, solo es para tener nocion
// del recorrido
fun inorderTree(node: TreeNode<String>, visit: (String) -> Unit){
    when (node.children.size){
        0 -> visit(node.value) // Es una hoja
        1 -> {
            inorderTree(node.children[0], visit) // recorrer un hijo
            visit(node.value)
        }
        2 -> {
            inorderTree(node.children[0], visit) // recorrer el hijo izquierdo
            visit(node.value)
            inorderTree(node.children[1], visit) // recorrer el hijo derecho
        }
        else -> throw IllegalArgumentException("Nodo con más de 2 hijos")
    }
} 





fun main() {
    val file = File("src/main/kotlin/Ast.txt")
    val lines = file.readLines()

    val formattedLines = ArrayList<String>()

    // Cambiar ? a |ε
    // Cambiar + a aa*
    for (line in lines) {
        var linea_formateada = sustituirOpcional(line)
        linea_formateada = sustituirMas(linea_formateada)
        formattedLines.add(linea_formateada)
    }

    println("Lineas formateadas: $formattedLines")

    // Construccion de AST
    for ((index, line) in formattedLines.withIndex()) {
        val postfixStr = infixToPostfix(line)
        println("Postfix: $postfixStr")
        val tokens = tokenize(postfixStr)
        println("Tokens: $tokens")

        [a, b, ., c, .]

        val stack = ArrayDeque<TreeNode<String>>()

        for (tok in tokens) {
            when {
                // operador binario
                tok.length == 1 && tok[0] in BINARY_OPERATORS -> {
                    val right = stack.removeFirst()
                    val left = stack.removeFirst()
                    val node = TreeNode(tok)
                    node.addChild(left)
                    node.addChild(right)
                    stack.addFirst(node)
                }
                // operador unario
                tok.length == 1 && tok[0] in UNARY_OPERATORS -> {
                    val child = stack.removeFirst()
                    val node = TreeNode(tok)
                    node.addChild(child)
                    stack.addFirst(node)
                }
                else -> {
                    // operando (literal, clase, escape, ε)
                    stack.addFirst(TreeNode(tok))
                }
            }
        }

        // Obtener raíz y renderizar
        val root = stack.removeFirst()
        val dot  = generateDot(root)
        val outputFile = File("ast_$index.png")
        renderDot(dot, outputFile)
        println("AST generado en: ${outputFile.absolutePath}")
    }
}