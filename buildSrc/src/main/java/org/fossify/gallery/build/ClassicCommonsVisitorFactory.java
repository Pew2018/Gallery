package org.fossify.gallery.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

/** Bounded compatibility patch for Commons 7.1.3; no file/security callbacks are changed. */
public abstract class ClassicCommonsVisitorFactory implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    @Override public boolean isInstrumentable(ClassData data) {
        return data.getClassName().equals("org.fossify.commons.extensions.ActivityKt")
            || data.getClassName().equals("org.fossify.commons.views.Breadcrumbs")
            || data.getClassName().startsWith("org.fossify.gallery.");
    }

    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        return visitor(context.getCurrentClassData().getClassName(), next);
    }

    public static ClassVisitor visitor(String name, ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            private int shows = 0;
            private int methods = 0;
            private final boolean activity = name.equals("org.fossify.commons.extensions.ActivityKt");
            private final boolean breadcrumbs = name.equals("org.fossify.commons.views.Breadcrumbs");
            private final String hook = "org/fossify/gallery/helpers/ClassicCommonsHooks";
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, method, desc, signature, exceptions);
                if (activity && method.equals("getAlertDialogBuilder") && desc.equals("(Landroid/app/Activity;)Landroidx/appcompat/app/AlertDialog$Builder;")) {
                    methods++;
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ARETURN) super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "prepareBuilder",
                                "(Landroidx/appcompat/app/AlertDialog$Builder;)Landroidx/appcompat/app/AlertDialog$Builder;", false);
                            super.visitInsn(opcode);
                        }
                    };
                }
                if (activity && method.equals("setupDialogStuff") && desc.startsWith("(Landroid/app/Activity;Landroid/view/View;")) {
                    methods++;
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitMethodInsn(int opcode, String owner, String called, String descriptor, boolean itf) {
                            boolean show = opcode == Opcodes.INVOKEVIRTUAL && (owner.equals("androidx/appcompat/app/AlertDialog") || owner.equals("android/app/Dialog")
                                || owner.equals("androidx/appcompat/app/AppCompatDialog"))
                                && called.equals("show") && descriptor.equals("()V");
                            if (show) super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(opcode, owner, called, descriptor, itf);
                            if (show) {
                                shows++;
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitInsn(Opcodes.SWAP);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "onDialogShown",
                                    "(Landroid/app/Activity;Landroidx/appcompat/app/AlertDialog;Landroid/view/View;)V", false);
                            }
                        }
                    };
                }
                if (breadcrumbs && method.equals("handleRootStickiness") && desc.equals("(I)V")) {
                    methods++;
                    target.visitCode();
                    target.visitVarInsn(Opcodes.ALOAD, 0);
                    target.visitVarInsn(Opcodes.ILOAD, 1);
                    target.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "positionBreadcrumbRoot", "(Landroid/view/ViewGroup;I)V", false);
                    target.visitInsn(Opcodes.RETURN);
                    target.visitMaxs(2, 2);
                    target.visitEnd();
                    return null;
                }
                if (breadcrumbs && (method.equals("recomputeStickyRootLocation") || method.equals("setBreadcrumb")
                    || method.equals("onScrollChanged") || method.equals("scrollToSelectedItem"))) {
                    methods++;
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            if (method.equals("recomputeStickyRootLocation")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "breadcrumbRootLeft", "(Landroid/view/ViewGroup;)I", false);
                                super.visitVarInsn(Opcodes.ISTORE, 1);
                            } else if (method.equals("scrollToSelectedItem")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "hasBreadcrumbItems", "(Landroid/view/ViewGroup;)Z", false);
                                Label hasItems = new Label();
                                super.visitJumpInsn(Opcodes.IFNE, hasItems);
                                super.visitInsn(Opcodes.RETURN);
                                super.visitLabel(hasItems);
                            } else {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "clearBreadcrumbPresses", "(Landroid/view/ViewGroup;)V", false);
                            }
                        }
                        @Override public void visitInsn(int opcode) {
                            if (method.equals("setBreadcrumb") && opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "styleBreadcrumbs", "(Landroid/view/ViewGroup;)V", false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if (!activity && !breadcrumbs) return new MethodVisitor(Opcodes.ASM9, target) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String descriptor, boolean itf) {
                        super.visitMethodInsn(opcode, owner, called, descriptor, itf);
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("androidx/appcompat/app/AlertDialog$Builder")
                            && called.equals("show") && descriptor.equals("()Landroidx/appcompat/app/AlertDialog;")) {
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "onStandaloneDialogShown",
                                "(Landroidx/appcompat/app/AlertDialog;)V", false);
                        }
                    }
                };
                return target;
            }
            @Override public void visitEnd() {
                // Fail closed if a future Commons update changes the patched structure.
                if ((activity && (methods != 2 || shows != 2)) || (breadcrumbs && methods != 5)) {
                    throw new IllegalStateException("Commons 7.1.3 classic patch mismatch: " + name + " methods=" + methods + " shows=" + shows);
                }
                super.visitEnd();
            }
        };
    }
}
