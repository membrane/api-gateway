package com.predic8.membrane.core.interceptor.soap;

import com.predic8.schema.*;
import com.predic8.schema.creator.*;
import com.predic8.schema.restriction.*;
import com.predic8.schema.restriction.facet.*;

import static com.predic8.soamodel.Consts.SCHEMA_NS;

public class TestCreator extends AbstractSchemaCreator<TestCreatorContext> {


    @Override
    public void createElement(Element element, TestCreatorContext ctx) {
        var type = element.getType() != null ? element.getSchema().getType(element.getType()) : element.getEmbeddedType();
        if (type instanceof SimpleType st) {
            ctx.add(element.getName() + ":");
            ctx.indent();
            type.create(this,ctx);
            return;
        }
        if(type != null && !(type instanceof BuiltInSchemaType)){
            System.out.println("type = " + type);
            ctx.add(element.getName() + ":");
            ctx.indent();
            ctx.add("type: object");
//            ctx.add("properties:");
//            ctx.indent();
            type.create(this, ctx);
            return;
        }

        // embedded element

        ctx.add(element.getName() + ":");
        ctx.indent();
        ctx.add("type: " + element.getType().getLocalPart());

        //writeInputForBuildInType(element, ctx)
    }

    @Override
    public void createAnnotation(Annotation annotation, TestCreatorContext ctx) {
        annotation.getDocumentations().forEach(d -> ctx.add("description: "+ d.getContent()));
    }

//    @Override
//    public void createSimpleType(SimpleType simpleType, TestCreatorContext ctx) {
//        ctx.add(simpleType.getRestriction().getBuildInTypeName());
//        simpleType.getRestriction().create(this,ctx);
//    }


    @Override
    public void createPatternFacet(PatternFacet facet, Object ctx) {
        System.out.println("TestCreator.createPatternFacet");
        if (ctx instanceof TestCreatorContext tcc) {
            tcc.add("pattern: ");
        }
    }

    @Override
    public void createSimpleType(SimpleType simpleType, TestCreatorContext ctx) {
        ctx.add("type: " + simpleType.getBuildInTypeName());
        super.createSimpleType(simpleType, ctx);
    }

    @Override
    public void createMaxLengthFacet(MaxLengthFacet facet, TestCreatorContext ctx) {
        ctx.add("maxLength: " + facet.getValue());
    }

    /**
     * Move annotation maybe in super class
     * @param complexType
     * @param ctx
     */
    @Override
    public void createComplexType(ComplexType complexType, TestCreatorContext ctx) {
        Annotation annotation = complexType.getAnnotation();
        if (annotation != null) {
            annotation.create(this, ctx);
        }
        ctx.add("properties:");
        ctx.indent();
        super.createComplexType(complexType, ctx);
    }

    @Override
    public void createSimpleRestriction(BaseRestriction restriction, TestCreatorContext  ctx){
        if(restriction.getBase() == null && restriction.getChildSimpleType() != null) {
//            builder.'xsd:restriction'{
                restriction.getChildSimpleType().create(this, ctx);
                restriction.getFacets().forEach(facet -> facet.create(this, ctx));
//                }

        } else {
            var prefix = restriction.getBase().getNamespaceURI() == SCHEMA_NS ? "xsd" : restriction.getPrefix(restriction.getBase().getNamespaceURI());
//            builder.'xsd:restriction'(base : "$prefix${prefix?':':''}${restriction.base.localPart}"){
                restriction.getFacets().forEach(facet -> facet.create(this, ctx));
//            }
        }
    }

}
