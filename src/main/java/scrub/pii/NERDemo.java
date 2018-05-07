package scrub.pii;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.time.SUTime;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class NERDemo {
    public static void trainClassifier() {
        String prop = "d:/corenlp/demo/ner_training.prop";
        Properties props = StringUtils.propFileToProperties(prop);
        String serializeToPath = props.getProperty("serializeTo");;
        SeqClassifierFlags flags = new SeqClassifierFlags(props);
        CRFClassifier<CoreLabel> crf = new CRFClassifier<CoreLabel>(flags);
        crf.train();
        crf.serializeClassifier(serializeToPath);
    }

    public static void main(String[] args) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String referenceDate = dateFormat.format(new Date());

        trainClassifier();

        // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref, regexner");
        props.put("regexner.ignorecase", "true");
        props.put("ner.model", "d:/corenlp/demo/ner-model.ser.gz,d:/corenlp/english.all.3class.distsim.crf.ser.gz");
        props.put("regexner.mapping", "d:/corenlp/demo/regexner/ticketmaster_names.tsv,d:/corenlp/demo/regexner/ticketmaster_genres.tsv");
        props.put("regexner.verbose", "true");
        props.put("regexner.backgroundSymbol", "O,PERSON,ORGANIZATION,ATTRACTION");
        props.put("ner.combinationMode", "HIGH_RECALL");


        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        pipeline.addAnnotator(new TimeAnnotator("sutime", props));

        String text = "Adele concert tickets cheap price. Cheap price. " +
                "From 50 to 80$. " +
                "From 10 to 20$. " +
                "fifty dollars.";

        // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);
        document.set(CoreAnnotations.DocDateAnnotation.class,	referenceDate);

        // run all Annotators on this text
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        for (CoreMap sentence : sentences) {
            System.out.println(sentence.toString());
            String prevNeToken = "O";
            String currNeToken = "O";
            boolean newToken = true;
            StringBuilder sb = new StringBuilder();

            // traversing the words in the current sentence
            // a CoreLabel is a CoreMap with additional token-specific methods
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // this is the text of the token
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                // this is the NER label of the token
                currNeToken = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                if (currNeToken.equals("O")) {
                    // LOG.debug("Skipping '{}' classified as {}", word, currNeToken);
                    if (!prevNeToken.equals("O") && (sb.length() > 0)) {
                        String namedEntity = sb.toString();
                        String entityLabel = prevNeToken;
                        System.out.println("TYPE " + entityLabel + ", VALUE " + namedEntity);
                        sb.setLength(0);
                        newToken = true;
                    }
                }

                else if (newToken) {
                    newToken = false;
                    sb.append(word);
                }

                else if (currNeToken.equals(prevNeToken)) {
                    sb.append(" " + word);
                } else {
                    String namedEntity = sb.toString();
                    String entityLabel = prevNeToken;
                    System.out.println("TYPE " + entityLabel + ", VALUE " + namedEntity);
                    sb.setLength(0);
                    sb.append(word);
                }
                prevNeToken = currNeToken;
            }
            if(sb.toString().length() != 0) {
                String namedEntity = sb.toString();
                String entityLabel = prevNeToken;
                System.out.println("TYPE " + entityLabel + ", VALUE " + namedEntity);
            }
            System.out.println();
        }


        System.out.println("\n\nRecognized dates");
        List<CoreMap> timexAnnsAll = document.get(TimeAnnotations.TimexAnnotations.class);
        for (CoreMap cm : timexAnnsAll) {
            SUTime.Temporal temporal = cm.get(TimeExpression.Annotation.class).getTemporal();
            List<CoreLabel> tokens = cm.get(CoreAnnotations.TokensAnnotation.class);
            System.out.println(cm.toString() + " RECOGNIZED DATE " + temporal);
        }
    }
}
