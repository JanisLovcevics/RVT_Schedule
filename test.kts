import java.util.regex.*

val input = "(Sports) ➔ Matemātika I - Moved from period: 4 - 5, Aizvietošana: (Ģirts Magone) ➔ Zeikats Lourenss, Kabineta nomaiņa: (1_sporta zāle) ➔ 514 (40)P"
var text = input.replace("➔", "->")

val teacherSubstMatch = Regex("""Aizvietošana:\s*\(([^)]+)\)\s*->\s*([^,]+)""").find(text)
if (teacherSubstMatch != null) {
    println("Teacher Old: " + teacherSubstMatch.groupValues[1])
    println("Teacher New: " + teacherSubstMatch.groupValues[2])
} else {
    println("Teacher Match Failed!")
}

val roomChangeMatch = Regex("""Kabineta nomaiņa:\s*\((.+)\)\s*->\s*([^,;]+)""").find(text)
if (roomChangeMatch != null) {
    println("Room Old: " + roomChangeMatch.groupValues[1])
    println("Room New: " + roomChangeMatch.groupValues[2])
} else {
    println("Room Match Failed!")
}
