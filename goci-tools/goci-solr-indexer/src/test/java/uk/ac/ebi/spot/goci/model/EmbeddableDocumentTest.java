package uk.ac.ebi.spot.goci.model;

import org.junit.Before;
import org.junit.Test;
import uk.ac.ebi.spot.goci.builder.AssociationBuilder;
import uk.ac.ebi.spot.goci.exception.DocumentEmbeddingException;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.fail;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 14/02/15
 */
public class EmbeddableDocumentTest {
    private Study study;
    private Association association;
    private AssociationReport associationReport;
    private DiseaseTrait diseaseTrait;

    private StudyDocument studyDoc;
    private AssociationDocument associationDoc;
    private DiseaseTraitDocument traitDoc;

    @Before
    public void setUp() {
        Housekeeping h = new Housekeeping();
        h.setLastUpdateDate(new Date());
        h.setCatalogPublishDate(new Date());

        this.study = new Study("author",
                               new Date(),
                               "publication",
                               "title",
                               "initial sample size",
                               "replicate sample size",
                               "123456",
//                               false,
//                               false,
                               false,
                               true,
                               false,
                               null,
                               "qualifier",
                               false,
                               false,
                               "study design comment",
                               "GCST999999",
                               false,
                               false,
                               Collections.EMPTY_LIST,
                               Collections.EMPTY_LIST,
                               Collections.EMPTY_LIST,
                               null,
                               Collections.EMPTY_LIST,
                               h, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST,
                               Collections.EMPTY_LIST);

        study.setId(1l);
        this.studyDoc = new StudyDocument(study);

        this.association = new AssociationBuilder().setPvalueExponent(1)
                .setPvalueMantissa(1)
                .setRiskFrequency(String.valueOf(0.5))
                .setPvalueDescription("(test)")
                .setRange("[NR]")
                .setOrPerCopyNum((float) 1.04)
                .setOrPerCopyRecip((float) 0.94)
                .setMultiSnpHaplotype(false)
                .setSnpInteraction(false)
                .setSnpApproved(false)
                .setSnpType("novel")
                .build();
        association.setId(2l);
        this.associationDoc = new AssociationDocument(association);

        this.diseaseTrait = new DiseaseTrait((long) 870, "Breast cancer");
        this.traitDoc = new DiseaseTraitDocument(diseaseTrait);
    }

    @Test
    public void testEmbed() {
        try {
            studyDoc.embed(associationDoc);
        }
        catch (DocumentEmbeddingException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testEmbedTrait() {
        try {
            traitDoc.embed(associationDoc);
        }
        catch (DocumentEmbeddingException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testIntrospection() {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(studyDoc.getClass());
            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                System.out.println("Display name: " + pd.getDisplayName());
                System.out.println("Name: " + pd.getName());
                System.out.println("Read method: " + pd.getReadMethod());
                System.out.println("\t" + pd);
            }

        }
        catch (IntrospectionException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testTraitIntrospection() {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(traitDoc.getClass());
            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                System.out.println("Display name: " + pd.getDisplayName());
                System.out.println("Name: " + pd.getName());
                System.out.println("Read method: " + pd.getReadMethod());
                System.out.println("\t" + pd);
            }

        }
        catch (IntrospectionException e) {
            e.printStackTrace();
            fail();
        }
    }
}
