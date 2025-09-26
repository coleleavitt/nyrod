import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import java.util.Base64

class Loader : ClassFileTransformer {

    override fun transform(
        loader: ClassLoader?,
        className: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray? {
        // Chain the patches.
        val result = bigintPatch(className, classfileBuffer)
            ?: bountyPatch(className, classfileBuffer)
            ?: burpPatch1(className, classfileBuffer)
            ?: burpPatch2(className, classfileBuffer)

        if (result != null) {
            info("Patched class: $className")
        }

        return result
    }

    private fun burpPatch2(className: String, classBytes: ByteArray): ByteArray? {
        if (!className.startsWith("burp/") || classBytes.size < 110000) return null
        val cr = ClassReader(classBytes)
        val cn = ClassNode()
        cr.accept(cn, 0)

        for (method in cn.methods) {
            if (method.desc == "([Ljava/lang/Object;Ljava/lang/Object;)V" && method.instructions.size() > 20000) {
                val insnList = method.instructions
                var exceptionCount = 0
                for (i in insnList.size() - 1 downTo 0) {
                    val instruction = insnList.get(i)
                    if (instruction is TypeInsnNode && instruction.opcode == Opcodes.NEW && instruction.desc == "java/lang/Exception") {
                        exceptionCount++
                        if (exceptionCount == 2) {
                            for (k in 0..5) {
                                val prevInstruction = insnList.get(i - k)
                                if (prevInstruction is JumpInsnNode) {
                                    method.instructions.insert(prevInstruction, JumpInsnNode(Opcodes.GOTO, prevInstruction.label))
                                    break
                                }
                            }
                            break
                        }
                    }
                }
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cn.accept(writer)
        return writer.toByteArray()
    }

    private fun bountyPatch(className: String, classBytes: ByteArray): ByteArray? {
        if (className != "feign/okhttp/OkHttpClient") return null
        val cr = ClassReader(classBytes)
        val cn = ClassNode()
        cr.accept(cn, 0)
        for (method in cn.methods) {
            if (method.name == "toFeignResponse" && method.desc == "(Lokhttp3/Response;Lfeign/Request;)Lfeign/Response;") {
                for (insnNode in method.instructions) {
                    if (insnNode is MethodInsnNode && insnNode.opcode == Opcodes.INVOKEVIRTUAL && insnNode.owner == "feign/Response\$Builder" && insnNode.name == "build") {
                        val injection = InsnList().apply {
                            add(VarInsnNode(Opcodes.ALOAD, 1))
                            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "feign/Request", "url", "()Ljava/lang/String;", false))
                            add(VarInsnNode(Opcodes.ALOAD, 1))
                            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "feign/Request", "body", "()[B", false))
                            add(MethodInsnNode(Opcodes.INVOKESTATIC, "FilterKt", "bountyFilter", "(Ljava/lang/String;[B)[B", false))
                            add(VarInsnNode(Opcodes.ASTORE, 2))
                            add(VarInsnNode(Opcodes.ALOAD, 2))
                            val outLabel = LabelNode()
                            add(JumpInsnNode(Opcodes.IFNULL, outLabel))
                            add(VarInsnNode(Opcodes.ALOAD, 2))
                            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "feign/Response\$Builder", "body", "([B)Lfeign/Response\$Builder;", false))
                            add(IntInsnNode(Opcodes.SIPUSH, 200))
                            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "feign/Response\$Builder", "status", "(I)Lfeign/Response\$Builder;", false))
                            add(outLabel)
                        }
                        method.instructions.insertBefore(insnNode, injection)
                    }
                }
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cn.accept(writer)
        return writer.toByteArray()
    }

    private fun bigintPatch(className: String, classBytes: ByteArray): ByteArray? {
        if (className != "java/math/BigInteger") return null
        try {
            info("Attempting to patch BigInteger...")
            val reader = ClassReader(classBytes)
            val node = ClassNode()
            reader.accept(node, 0)
            val mn = node.methods.find { it.name == "oddModPow" && it.desc == "(Ljava/math/BigInteger;Ljava/math/BigInteger;)Ljava/math/BigInteger;" }
            if (mn != null) {
                info("Found oddModPow method, applying patch...")
                val encodedValue1 = "Nzg0NDk0NTUwNjUwNjI5NDAyNTc4MzE4ODEyMDM4ODczMjQ2MTAzMDYyNDI5MzkyNzY1OTAwODYzNjc5NjkyNDAxNTE1OTE3NDgzMjg4NDQ0NTUxMzYwMzk2OTkzODkxMjQzNTgxMzIzNzc2MDMxMTc0MTA0NzE2NTIwNjE4MTk2NTE3MDU0MDc1Mzk5MTgxMTUyODA1MDYyMTY3ODYzMzk5OTkzMzk3Nzg4MTA4NTI2MDYyNTc0NDQyOTU3Mzk3MzgwMjc2OTQ3MDA5MTEwODE0ODM3NjIwMTg3NzMyMzAwOTEzMTc0NjMzODcxODM4NzIxMzQ1Njk5MzEwOTc5NDc1MTA2MjI4NDU1NTQwNjYyOTMxNTU0MTc2ODQ4NDI4MTYxNTU5MzgyMjEzNjY0NTkwOTQ5NTg1MDI5MTkxNTQyOTQzMDg1OTU3ODIzMjY1NTAzMzA0MzM2NzQyNzY2MDY3NjI0NjI1NzM2NzM1MjU0MzU0Mzk4OTQ1MDQ2MDA2MzExMjQ0NzA3MzUzNDE3MTM3NDQ5ODY4OTM4OTY4NzMwMjI2NzcxNTkxMjQ2NjI1ODY1OTQ3ODkzNTk1NjIyMTE0NTAzNTQ3MTA4MzczMDA5NDcyOTYwNTk5NDQ0MTQ3MDYwMTExOTcxMzUwNDE5ODgzNjg2NjY3NDY1MjkzMzc5OTc2NjE5MDA4NzY0MzU1OTY1NTgwNDY1NTA0MjgwNDMyNjgzMTUxMTYwMDc4NTU2NTY2NTQwMDgzNDQ3NzE1ODIyNzAyNzI3NjY3MTc3MDIyMTExNDMxODE0MzQ3Mzc0NDIzMTA3MjMxNDgyNzY4OTk0NTU4Mjk3MjMxNTAxODA3NDcwMzE4MjEzMzQ4NTQ1NjIzNjE2MzIzNjgzMDQzOTMzNTM2NzE2NTkyNzYxMzUyMDI2MjAwNTg3OTgzNTA4Njg4Njc1ODA4MTQwNzYxNzkxMjM1ODkwODk0NDIxMzQ1MTY4MjY4OTQ0MjcyMDAzNzY2NTExNzIzODYzNTA5MjQ4OTAxOTA3NzA2NTgzNDEyNjQ3NTg2MzA0OTE5MTY5MjEzMTE5NDYzMTY4OTg4NjU0NzcyNDY2OTI1OTcwNTU5NjI3NjEwNzIzMDg4NDk4ODU2MDk4MDA1OTU5MzMxNDU3ODc4NDg2OTQ1MTUwMDE5NjIzNTA5NDg4NTgwODk1Mzg2Mzk5MjYyNTY5ODQ0MDk3MTMxMDc1Nzg2NTAwOTQyNTMwNDQzMDY1NTM4NjkzMTEzMTUxNzUxMzY4NjIxNDg3NzE3MTE2MTcyMDQ5ODY1MDY1MDg0MzM0MTU1NDY0NDk2MjU4NDExOTQzNDUwMzAyOTUzNTkwNjA0OTQwOTUwOTEyNzg3MDQ5MjUxNjg2ODg0MDc2MDM0ODEyNTQzNTk0NjY0ODA2Njg3ODMwMDAwOTgxMzIwOTc5MDI1MjMwODg4NDA0ODcyNTI5MzQyMTgzOTU5MzkyMzc1Mzk2MjMyNTgyODc4NTgwOTUzMTk3MjAwNjEyMjUxNjE2OTE3NDE2ODYxMTg0Mzg4ODA1MzYxODAwNjYwNzcxNDgxMTM5NzY0NDM1MjU4NTA1ODMxMTE0MjUyNDkxOTMyNDg3MTE1NTE3NDE1ODc1MzY0NDE5"
                val decodedValue1 = String(Base64.getDecoder().decode(encodedValue1))
                val instructions = InsnList().apply {
                    add(LdcInsnNode(decodedValue1))
                    add(VarInsnNode(Opcodes.ALOAD, 2))
                    add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/math/BigInteger", "toString", "()Ljava/lang/String;", false))
                    add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false))
                    val label = LabelNode()
                    add(JumpInsnNode(Opcodes.IFEQ, label))
                    add(TypeInsnNode(Opcodes.NEW, "java/math/BigInteger"))
                    add(InsnNode(Opcodes.DUP))
                    val encodedValue2 = "NzcyNDY2NDg1OTkzNTMyNjE2MjY1NjAyMDA0Nzg3NTkzOTkwNTcxOTk4MzU0NzYyNzY5NTE3NTU2ODI0OTQ3NDYyMzY0NzMzODQ2OTg4ODk2NzY0NzkxNzg2NDE4Mjg0MDQ4MzA4NDA4MDM1ODk4MTIzODAwMDg4Mzg1MTQ1NTk5OTE0NDgyODY2OTgyMTIxMzM3MjQyMjY3MTExOTk2NzMwNTg5ODMzMDA4OTY1NTg2OTA0NjE5NjI0MjEwNjAwMTQzODE1MDM0NTU1MTA3NjI2MjgxMzA2NzkwNjkwMjQ3MzgwMjc3NDAxMjIzNjU4MjEzNjg2ODM2MjYwMDUxNzQwNTEzMzY5NTI1MjI5OTQ1MTQ2NTQ1NzQ2MjMyMjg4NjQ0ODM0MDExMDgyMjcyMzczOTIzODQwNjc0MTgzODQxMTUyNzIxMjgyOTM0ODk2MTQ3NjM2NjU5Njg1MTk2MTEwMTkzMTcwODU5MDM2MjMwNjEzOTQyNDczMDU3MzUwNTU4Njk2MDc5NzgxMzI5NzI0NzIzNTM4NjEyMDY1NDUxMjcxMTE3OTQ2NjUzMTcxODkxNDYzNzAxMjA2MDA1MTMyNDc0MjQ5MTQzMzc3MjgwOTU2NjM3Mjc0ODEyMDE3NzIyOTY2OTYyMDM4MTc3NjU4ODI2NjgyOTI4MTc3NDE5OTY3MTIwNjE1NzIzNDEyNzE1MjgwNjI3MTQwMzUyNjY2NTEzNzYxODg3NjU1NjgzODgxMTI3OTQxMDYyNTIxNjA4NjIyMTUwMDU3Mzc1MzAyNzczODE3MTI4MDQzMzQxNTUxMTA0MzM1OTI1MzA3NTI0MzYyNDYzMTU4NTMyMDE0MzQ1OTIwNDAxNTI5MTMxNjg5OTcyNTExNzExMTQ1MzY2Mzg0MzEzMzU3MzMxMjE5OTE4MDA3OTAyMTYyMjcyOTQ3ODEwMDkyNDM0NDAxNDIxMDc0NDM2Mjk3MTMwNTQzNDIwODU3Njc5MTczMjUwODc5MjIxNzAzMjM5NDYxMTY3MjQ1MTI4NzE4MDI3MjEwNTg1NDcwMDk3NzQxMzM4NDc2MTY3NjY0MjMxMzg2MjI0MzkzODI5MTczNzUzNTk3MzI5Njg0OTgxNTQ5NDQyMDMxMDA0ODg2MzA5MTc2ODA0Mjc0NTM2NTI0NDIxMjY5ODk2Njk5NjEzMTQ4NDI3MDE3MDExNjQ4ODA1OTY4MzI4MjEzODc5NTYxNzA0NjY0ODIzMjQ5ODA1MzQxNDMwODUwNzA4MzQ0OTU1OTIyODg2ODIyMTkxMzgwMjA2NTQzMzkzNTI1MzkxNTE5NzIyMjIyNTg2MzM5NjEyNzcwNTMxMDc0NTc3MzAzMzQ5NjI1NzkwMjA4MDQ3MDQ3MDM1OTMzOTk2MTM3ODMxMDg2NDYxNDk2NzkzMjM1NDEwNDQ1NzY5NjU5OTgyMzU1NTY3MTgwMDczNTA5NDA1MDc3MDk0Nzg0OTc1NzEzNTc1OTM3NjcxNTYwMzk5NzM0NTQ0ODczOTY3MzM5MjgxOTAwNDQzNDU4OTA0MDUyMzQ1Njk4NzUyODE3NTA0NDQ5ODEzNTA4NjY0ODM1MjQ3MjA2NzI3NDkzODI4NzQwNTAwNzUxNzI3MzA1OTQ4ODcwMTk3"
                    val decodedValue2 = String(Base64.getDecoder().decode(encodedValue2))
                    add(LdcInsnNode(decodedValue2))
                    add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/math/BigInteger", "<init>", "(Ljava/lang/String;)V", false))
                    add(VarInsnNode(Opcodes.ASTORE, 2))
                    add(label)
                }
                mn.instructions.insert(instructions)
                val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
                node.accept(writer)
                return writer.toByteArray()
            }
            return null
        } catch (e: Exception) {
            error("BigInteger patch failed: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun burpPatch1(className: String, classBytes: ByteArray): ByteArray? {
        if (!className.startsWith("burp/") || classBytes.size <= 110000) return null
        val cr = ClassReader(classBytes)
        val cn = ClassNode()
        cr.accept(cn, 0)
        for (method in cn.methods) {
            if (method.desc == "([Ljava/lang/Object;Ljava/lang/Object;)V" && method.instructions.size() > 20000) {
                method.instructions.clear()
                method.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
                // V V V FIX THIS LINE V V V
                method.instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "FilterKt", "burpFilter", "([Ljava/lang/Object;)V", false))
                method.instructions.add(InsnNode(Opcodes.RETURN))
                method.exceptions.clear()
                method.tryCatchBlocks.clear()
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cn.accept(writer)
        return writer.toByteArray()
    }
}