/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleAnnotationValueVisitor7;
import javax.lang.model.util.SimpleElementVisitor7;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.lang.model.util.Types;

/**
 * A random assortment of methods to help with implementing the Java API checks made public so that
 * extenders don't have to reinvent the wheel.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Util {

    private static class StringBuilderAndState<T> {
        final StringBuilder bld = new StringBuilder();
        final Set<T> visitedObjects = new HashSet<>();
        boolean visitingMethod;
    }

    private static SimpleTypeVisitor7<Void, StringBuilderAndState<TypeMirror>> toUniqueStringVisitor = new SimpleTypeVisitor7<Void, StringBuilderAndState<TypeMirror>>() {

        @Override
        public Void visitPrimitive(PrimitiveType t, StringBuilderAndState<TypeMirror> state) {
            switch (t.getKind()) {
            case BOOLEAN:
                state.bld.append("boolean");
                break;
            case BYTE:
                state.bld.append("byte");
                break;
            case CHAR:
                state.bld.append("char");
                break;
            case DOUBLE:
                state.bld.append("double");
                break;
            case FLOAT:
                state.bld.append("float");
                break;
            case INT:
                state.bld.append("int");
                break;
            case LONG:
                state.bld.append("long");
                break;
            case SHORT:
                state.bld.append("short");
                break;
            default:
                break;
            }

            return null;
        }

        @Override
        public Void visitArray(ArrayType t, StringBuilderAndState<TypeMirror> bld) {
            bld.bld.append("[");
            t.getComponentType().accept(this, bld);
            bld.bld.append("]");
            return null;
        }

        @Override
        public Void visitTypeVariable(TypeVariable t, StringBuilderAndState<TypeMirror> state) {
            if (state.visitedObjects.contains(t)) {
                state.bld.append("%");
                return null;
            }

            state.visitedObjects.add(t);

            if (t.getLowerBound() != null && t.getLowerBound().getKind() != TypeKind.NULL) {
                t.getLowerBound().accept(this, state);
                state.bld.append("-");
            }

            t.getUpperBound().accept(this, state);
            state.bld.append("+");
            return null;
        }

        @Override
        public Void visitWildcard(WildcardType t, StringBuilderAndState<TypeMirror> state) {
            if (t.getSuperBound() != null) {
                t.getSuperBound().accept(this, state);
                state.bld.append("-");
            }

            if (t.getExtendsBound() != null) {
                t.getExtendsBound().accept(this, state);
                state.bld.append("+");
            }

            return null;
        }

        @Override
        public Void visitExecutable(ExecutableType t, StringBuilderAndState<TypeMirror> state) {
            visitTypeVars(t.getTypeVariables(), state);

            t.getReturnType().accept(this, state);
            state.bld.append("(");

            Iterator<? extends TypeMirror> it = t.getParameterTypes().iterator();
            if (it.hasNext()) {
                it.next().accept(this, state);
            }
            while (it.hasNext()) {
                state.bld.append(",");
                it.next().accept(this, state);
            }
            state.bld.append(")");

            if (!t.getThrownTypes().isEmpty()) {
                state.bld.append("throws:");
                it = t.getThrownTypes().iterator();

                it.next().accept(this, state);
                while (it.hasNext()) {
                    state.bld.append(",");
                    it.next().accept(this, state);
                }
            }

            return null;
        }

        @Override
        public Void visitNoType(NoType t, StringBuilderAndState<TypeMirror> state) {
            switch (t.getKind()) {
            case VOID:
                state.bld.append("void");
                break;
            case PACKAGE:
                state.bld.append("package");
                break;
            default:
                break;
            }

            return null;
        }

        @Override
        public Void visitDeclared(DeclaredType t, StringBuilderAndState<TypeMirror> state) {
            state.bld.append(((TypeElement) t.asElement()).getQualifiedName());
            visitTypeVars(t.getTypeArguments(), state);
            return null;
        }

        private void visitTypeVars(List<? extends TypeMirror> vars, StringBuilderAndState<TypeMirror> state) {
            if (!vars.isEmpty()) {
                state.bld.append("<");
                Iterator<? extends TypeMirror> it = vars.iterator();
                it.next().accept(this, state);

                while (it.hasNext()) {
                    state.bld.append(",");
                    it.next().accept(this, state);
                }

                state.bld.append(">");
            }
        }
    };

    private static SimpleTypeVisitor7<Void, StringBuilderAndState<TypeMirror>> toHumanReadableStringVisitor = new SimpleTypeVisitor7<Void, StringBuilderAndState<TypeMirror>>() {

        @Override
        public Void visitPrimitive(PrimitiveType t, StringBuilderAndState<TypeMirror> state) {
            switch (t.getKind()) {
            case BOOLEAN:
                state.bld.append("boolean");
                break;
            case BYTE:
                state.bld.append("byte");
                break;
            case CHAR:
                state.bld.append("char");
                break;
            case DOUBLE:
                state.bld.append("double");
                break;
            case FLOAT:
                state.bld.append("float");
                break;
            case INT:
                state.bld.append("int");
                break;
            case LONG:
                state.bld.append("long");
                break;
            case SHORT:
                state.bld.append("short");
                break;
            default:
                break;
            }

            return null;
        }

        @Override
        public Void visitArray(ArrayType t, StringBuilderAndState<TypeMirror> state) {
            state.bld.append("[");
            t.getComponentType().accept(this, state);
            state.bld.append("]");
            return null;
        }

        @Override
        public Void visitTypeVariable(TypeVariable t, StringBuilderAndState<TypeMirror> state) {
            if (state.visitedObjects.contains(t)) {
                state.bld.append(t.asElement().getSimpleName());
                return null;
            }

            state.visitedObjects.add(t);

            state.bld.append(t.asElement().getSimpleName());

            if (!state.visitingMethod) {
                if (t.getLowerBound() != null && t.getLowerBound().getKind() != TypeKind.NULL) {
                    state.bld.append(" super ");
                    t.getLowerBound().accept(this, state);
                }

                state.bld.append(" extends ");
                t.getUpperBound().accept(this, state);
            }

            return null;
        }

        @Override
        public Void visitWildcard(WildcardType t, StringBuilderAndState<TypeMirror> state) {
            state.bld.append("?");
            if (t.getSuperBound() != null) {
                state.bld.append(" super ");
                t.getSuperBound().accept(this, state);
            }

            if (t.getExtendsBound() != null) {
                state.bld.append(" extends ");
                t.getExtendsBound().accept(this, state);
            }

            return null;
        }

        @Override
        public Void visitExecutable(ExecutableType t, StringBuilderAndState<TypeMirror> state) {
            visitTypeVars(t.getTypeVariables(), state);

            state.visitingMethod = true;

            t.getReturnType().accept(this, state);
            state.bld.append("(");

            Iterator<? extends TypeMirror> it = t.getParameterTypes().iterator();
            if (it.hasNext()) {
                it.next().accept(this, state);
            }
            while (it.hasNext()) {
                state.bld.append(", ");
                it.next().accept(this, state);
            }
            state.bld.append(")");

            if (!t.getThrownTypes().isEmpty()) {
                state.bld.append(" throws ");
                it = t.getThrownTypes().iterator();

                it.next().accept(this, state);
                while (it.hasNext()) {
                    state.bld.append(", ");
                    it.next().accept(this, state);
                }
            }

            state.visitingMethod = false;
            return null;
        }

        @Override
        public Void visitNoType(NoType t, StringBuilderAndState<TypeMirror> state) {
            switch (t.getKind()) {
            case VOID:
                state.bld.append("void");
                break;
            case PACKAGE:
                state.bld.append("package");
                break;
            default:
                break;
            }

            return null;
        }

        @Override
        public Void visitDeclared(DeclaredType t, StringBuilderAndState<TypeMirror> state) {
            state.bld.append(((TypeElement) t.asElement()).getQualifiedName());
            visitTypeVars(t.getTypeArguments(), state);
            return null;
        }

        private void visitTypeVars(List<? extends TypeMirror> vars, StringBuilderAndState<TypeMirror> state) {
            if (!vars.isEmpty()) {
                state.bld.append("<");
                Iterator<? extends TypeMirror> it = vars.iterator();
                it.next().accept(this, state);

                while (it.hasNext()) {
                    state.bld.append(", ");
                    it.next().accept(this, state);
                }

                state.bld.append(">");
            }
        }
    };

    private static SimpleElementVisitor7<Void, StringBuilderAndState<TypeMirror>> toHumanReadableStringElementVisitor = new SimpleElementVisitor7<Void, StringBuilderAndState<TypeMirror>>() {
        @Override
        public Void visitVariable(VariableElement e, StringBuilderAndState<TypeMirror> state) {
            Element enclosing = e.getEnclosingElement();
            if (enclosing instanceof TypeElement) {
                enclosing.accept(this, state);
                state.bld.append(".").append(e.getSimpleName());
            } else if (enclosing instanceof ExecutableElement) {
                if (state.visitingMethod) {
                    //we're visiting a method, so we need to output the in a simple way
                    e.asType().accept(toHumanReadableStringVisitor, state);
                    //NOTE the names of method params seem not to be available
                    //stringBuilder.append(" ").append(e.getSimpleName());
                } else {
                    //this means someone asked to directly output a string representation of a method parameter
                    //in this case, we need to identify the parameter inside the full method signature so that
                    //the full location is available.
                    int paramIdx = ((ExecutableElement) enclosing).getParameters().indexOf(e);
                    enclosing.accept(this, state);
                    int openPar = state.bld.indexOf("(");
                    int closePar = state.bld.indexOf(")", openPar);

                    int paramStart = openPar + 1;
                    int curParIdx = -1;
                    for (int i = openPar + 1; i < closePar; ++i) {
                        if (state.bld.charAt(i) == ',') {
                            curParIdx++;
                            if (curParIdx == paramIdx) {
                                String par = state.bld.substring(paramStart, i);
                                state.bld.replace(paramStart, i, "===" + par + "===");
                            } else {
                                //accommodate for the space after commas for the second and further parameters
                                paramStart = i + (paramIdx == 0 ? 1 : 2);
                            }
                        }
                    }

                    if (++curParIdx == paramIdx) {
                        String par = state.bld.substring(paramStart, closePar);
                        state.bld.replace(paramStart, closePar, "===" + par + "===");
                    }
                }
            } else {
                state.bld.append(e.getSimpleName());
            }

            return null;
        }

        @Override
        public Void visitPackage(PackageElement e, StringBuilderAndState<TypeMirror> state) {
            state.bld.append(e.getQualifiedName());
            return null;
        }

        @Override
        public Void visitType(TypeElement e, StringBuilderAndState<TypeMirror> state) {
            state.bld.append(e.getQualifiedName());

            List<? extends TypeParameterElement> typePars = e.getTypeParameters();
            if (typePars.size() > 0) {
                state.bld.append("<");

                typePars.get(0).accept(this, state);
                for (int i = 1; i < typePars.size(); ++i) {
                    state.bld.append(", ");
                    typePars.get(i).accept(this, state);
                }
                state.bld.append(">");
            }

            return null;
        }

        @Override
        public Void visitExecutable(ExecutableElement e, StringBuilderAndState<TypeMirror> state) {
            state.visitingMethod = true;

            try {
                List<? extends TypeParameterElement> typePars = e.getTypeParameters();
                if (typePars.size() > 0) {
                    state.bld.append("<");

                    typePars.get(0).accept(this, state);
                    for (int i = 1; i < typePars.size(); ++i) {
                        state.bld.append(", ");
                        typePars.get(i).accept(this, state);
                    }
                    state.bld.append("> ");
                }

                e.getReturnType().accept(toHumanReadableStringVisitor, state);
                state.bld.append(" ");
                e.getEnclosingElement().accept(this, state);
                state.bld.append("::").append(e.getSimpleName()).append("(");

                List<? extends VariableElement> pars = e.getParameters();
                if (pars.size() > 0) {
                    pars.get(0).accept(this, state);
                    for (int i = 1; i < pars.size(); ++i) {
                        state.bld.append(", ");
                        pars.get(i).accept(this, state);
                    }
                }

                state.bld.append(")");

                List<? extends TypeMirror> thrownTypes = e.getThrownTypes();

                if (thrownTypes.size() > 0) {
                    state.bld.append(" throws ");
                    thrownTypes.get(0).accept(toHumanReadableStringVisitor, state);
                    for (int i = 1; i < thrownTypes.size(); ++i) {
                        state.bld.append(", ");
                        thrownTypes.get(i).accept(toHumanReadableStringVisitor, state);
                    }
                }

                return null;
            } finally {
                state.visitingMethod = false;
            }
        }

        @Override
        public Void visitTypeParameter(TypeParameterElement e, StringBuilderAndState<TypeMirror> state) {
            state.bld.append(e.getSimpleName());
            List<? extends TypeMirror> bounds = e.getBounds();
            if (bounds.size() > 0) {
                if (bounds.size() == 1) {
                    TypeMirror firstBound = bounds.get(0);
                    String bs = toHumanReadableString(firstBound);
                    if (!"java.lang.Object".equals(bs)) {
                        state.bld.append(" extends ").append(bs);
                    }
                } else {
                    state.bld.append(" extends ");
                    bounds.get(0).accept(toHumanReadableStringVisitor, state);
                    for (int i = 1; i < bounds.size(); ++i) {
                        state.bld.append(", ");
                        bounds.get(i).accept(toHumanReadableStringVisitor, state);
                    }
                }
            }

            return null;
        }
    };

    private Util() {

    }

    /**
     * To be used to compare types from different compilations (which are not comparable by standard means in Types).
     * This just compares the type names.
     *
     * @param t1 first type
     * @param t2 second type
     *
     * @return true if the types have the same fqn, false otherwise
     */
    public static boolean isSameType(@Nonnull TypeMirror t1, @Nonnull TypeMirror t2) {
        String t1Name = toUniqueString(t1);
        String t2Name = toUniqueString(t2);

        return t1Name.equals(t2Name);
    }

    @Nonnull
    public static String toHumanReadableString(@Nonnull Element element) {
        StringBuilderAndState<TypeMirror> state = new StringBuilderAndState<>();
        element.accept(toHumanReadableStringElementVisitor, state);
        return state.bld.toString();
    }

    /**
     * Represents the type mirror as a string in such a way that it can be used for equality comparisons.
     *
     * @param t type to convert to string
     */
    @Nonnull
    public static String toUniqueString(@Nonnull TypeMirror t) {
        StringBuilderAndState<TypeMirror> state = new StringBuilderAndState<>();
        t.accept(toUniqueStringVisitor, state);
        return state.bld.toString();
    }

    @Nonnull
    public static String toHumanReadableString(@Nonnull TypeMirror t) {
        StringBuilderAndState<TypeMirror> state = new StringBuilderAndState<>();
        t.accept(toHumanReadableStringVisitor, state);
        return state.bld.toString();
    }

    @Nonnull
    public static String toUniqueString(@Nonnull AnnotationValue v) {
        return toHumanReadableString(v);
    }

    @Nonnull
    public static String toHumanReadableString(@Nonnull AnnotationValue v) {
        return v.accept(new SimpleAnnotationValueVisitor7<String, Void>() {

            @Override
            protected String defaultAction(Object o, Void ignored) {
                return o.toString();
            }

            @Override
            public String visitType(TypeMirror t, Void ignored) {
                return toHumanReadableString(t);
            }

            @Override
            public String visitEnumConstant(VariableElement c, Void ignored) {
                return toHumanReadableString(c.asType()) + "." + c.getSimpleName().toString();
            }

            @Override
            public String visitAnnotation(AnnotationMirror a, Void ignored) {
                StringBuilder bld = new StringBuilder("@").append(toHumanReadableString(a.getAnnotationType()));

                if (!a.getElementValues().isEmpty()) {
                    bld.append("(");
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : a.getElementValues()
                        .entrySet()) {

                        bld.append(e.getKey().getSimpleName().toString()).append(" = ");
                        bld.append(e.getValue().accept(this, null));
                        bld.append(", ");
                    }
                    bld.replace(bld.length() - 2, bld.length(), "");
                    bld.append(")");
                }
                return bld.toString();
            }

            @Override
            public String visitArray(List<? extends AnnotationValue> vals, Void ignored) {
                StringBuilder bld = new StringBuilder("[");

                for (AnnotationValue v : vals) {
                    bld.append(v.accept(this, null));
                }

                bld.append("]");

                return bld.toString();
            }
        }, null);
    }

    @Nonnull
    public static List<TypeMirror> getAllSuperClasses(@Nonnull Types types, @Nonnull TypeMirror type) {
        List<? extends TypeMirror> superTypes = types.directSupertypes(type);
        List<TypeMirror> ret = new ArrayList<>();

        while (superTypes != null && !superTypes.isEmpty()) {
            TypeMirror superClass = superTypes.get(0);
            ret.add(superClass);
            superTypes = types.directSupertypes(superClass);
        }

        return ret;
    }

    @Nonnull
    public static List<TypeMirror> getAllSuperTypes(@Nonnull Types types, @Nonnull TypeMirror type) {
        ArrayList<TypeMirror> ret = new ArrayList<>();
        fillAllSuperTypes(types, type, ret);

        return ret;
    }

    public static void fillAllSuperTypes(@Nonnull Types types, @Nonnull TypeMirror type,
        @Nonnull List<TypeMirror> result) {
        List<? extends TypeMirror> superTypes = types.directSupertypes(type);

        for (TypeMirror t : superTypes) {
            result.add(t);
            fillAllSuperTypes(types, t, result);
        }
    }

    /**
     * Checks whether given type is a sub type or is equal to one of the provided types.
     * Note that this does not require the type to come from the same type "environment"
     * or compilation as the super types.
     *
     * @param type            the type to check
     * @param superTypes      the list of supposed super types
     * @param typeEnvironment the environment in which the type lives
     *
     * @return true if type a sub type of one of the provided super types, false otherwise.
     */
    public static boolean isSubtype(@Nonnull TypeMirror type, @Nonnull List<? extends TypeMirror> superTypes,
        @Nonnull Types typeEnvironment) {

        List<TypeMirror> typeSuperTypes = getAllSuperTypes(typeEnvironment, type);
        typeSuperTypes.add(0, type);

        for (TypeMirror t : typeSuperTypes) {
            String oldi = toUniqueString(t);
            for (TypeMirror i : superTypes) {
                String newi = toUniqueString(i);
                if (oldi.equals(newi)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nonnull
    public static Map<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> keyAnnotationAttributesByName(
        @Nonnull Map<? extends ExecutableElement, ? extends AnnotationValue> attributes) {
        Map<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> result = new LinkedHashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : attributes.entrySet()) {
            result.put(e.getKey().getSimpleName().toString(), e);
        }

        return result;
    }

    public static boolean isEqual(@Nonnull AnnotationValue oldVal, @Nonnull AnnotationValue newVal) {
        return oldVal.accept(new SimpleAnnotationValueVisitor7<Boolean, Object>() {

            @Override
            protected Boolean defaultAction(Object o, Object o2) {
                return o.equals(o2);
            }

            @Override
            public Boolean visitType(TypeMirror t, Object o) {
                if (!(o instanceof TypeMirror)) {
                    return false;
                }

                String os = toUniqueString(t);
                String ns = toUniqueString((TypeMirror) o);

                return os.equals(ns);
            }

            @Override
            public Boolean visitEnumConstant(VariableElement c, Object o) {
                return o instanceof VariableElement &&
                    c.getSimpleName().toString().equals(((VariableElement) o).getSimpleName().toString());
            }

            @Override
            public Boolean visitAnnotation(AnnotationMirror a, Object o) {
                if (!(o instanceof AnnotationMirror)) {
                    return false;
                }

                AnnotationMirror oa = (AnnotationMirror) o;

                String ot = toUniqueString(a.getAnnotationType());
                String nt = toUniqueString(oa.getAnnotationType());

                if (!ot.equals(nt)) {
                    return false;
                }

                if (a.getElementValues().size() != oa.getElementValues().size()) {
                    return false;
                }

                Map<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> aVals = keyAnnotationAttributesByName(
                    a.getElementValues());
                Map<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> oVals = keyAnnotationAttributesByName(
                    oa.getElementValues());

                for (Map.Entry<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> aVal : aVals
                    .entrySet()) {
                    String name = aVal.getKey();
                    Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> aAttr = aVal.getValue();
                    Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> oAttr = oVals.get(name);

                    if (oAttr == null) {
                        return false;
                    }

                    String as = toUniqueString(aAttr.getValue());
                    String os = toUniqueString(oAttr.getValue());

                    if (!as.equals(os)) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public Boolean visitArray(List<? extends AnnotationValue> vals, Object o) {
                if (!(o instanceof List)) {
                    return false;
                }

                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> ovals = (List<? extends AnnotationValue>) o;

                if (vals.size() != ovals.size()) {
                    return false;
                }

                for (int i = 0; i < vals.size(); ++i) {
                    if (!vals.get(i).accept(this, ovals.get(i).getValue())) {
                        return false;
                    }
                }

                return true;
            }
        }, newVal.getValue());
    }
}
