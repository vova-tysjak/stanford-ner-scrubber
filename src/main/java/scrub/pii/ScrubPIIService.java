package scrub.pii;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.NERCombinerAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScrubPIIService {

    public static void trainAndWrite(String modelOutPath, String prop, String trainingFilepath) {
        Properties props = StringUtils.propFileToProperties(prop);
        props.setProperty("serializeTo", modelOutPath);
        props.setProperty("trainFile", trainingFilepath);
//        props.put("regexner.posmatchtype", "MATCH_ALL_TOKENS");
        props.put("regexner.verbose", "true");
        props.put("regexner.backgroundSymbol", "O,PERSON,ORGANIZATION,NAME");
        props.put("regexner.ignorecase", "true");

        SeqClassifierFlags flags = new SeqClassifierFlags(props);
        CRFClassifier<CoreLabel> crf = new CRFClassifier<>(flags);
        crf.train();

        crf.serializeClassifier(modelOutPath);
    }

    public static CRFClassifier getModel(String modelPath) {
        return CRFClassifier.getClassifierNoExceptions(modelPath);
    }

    public static void tokenRegex() {
        String file = "PAYPAL *RENEETK XXX-XXX-XXXX CA";
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation document = new Annotation(file);
        pipeline.annotate(document);
        List<CoreLabel> tokens = new ArrayList<>();
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            // **using TokensRegex**
            tokens.addAll(sentence.get(CoreAnnotations.TokensAnnotation.class));
            TokenSequencePattern p1 = TokenSequencePattern.compile("fn");
            TokenSequenceMatcher matcher = p1.getMatcher(tokens);
            while (matcher.find())
                System.out.println("found");

            // **looking for the POS**
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                // this is the POS tag of the token
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                System.out.println("word is " + word + ", ne is " + ne);
            }
        }
    }

    public static void test() throws IOException, ClassNotFoundException {

        trainAndWrite("ner-model.ser.gz",
                "/Users/volodymyr/IdeaProjects/pii-ner-service/src/main/resources/ner.properties",
                "/Users/volodymyr/IdeaProjects/pii-ner-service/src/main/resources/regexner.txt");

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref, regexner");
        props.put("regexner.ignorecase", "true");
        props.put("ner.model", "ner-model.ser.gz");
        props.put("regexner.mapping", "/Users/volodymyr/IdeaProjects/pii-ner-service/src/main/resources/regexner.txt");
        props.put("regexner.verbose", "true");
        props.put("regexner.backgroundSymbol", "O,PERSON,ORGANIZATION,ATTRACTION");
        props.put("ner.combinationMode", "HIGH_RECALL");

        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        String text = "Adele concert tickets cheap price. Cheap price. " +
                "From 50 to 80$. " +
                "From 10 to 20$. " +
                "fifty dollars.";

        // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);

        // run all Annotators on this text
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    }

    private static String getFileWithUtil(String fileName) {

        String result = "";
        try {
            result = IOUtils.toString(ScrubPIIService.class.getClassLoader().getResourceAsStream(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private static void fileStreamUsingFiles(String fileName) {
        try {
            Stream<String> lines = Files.lines(Paths.get(fileName));
            System.out.println("<!-----Read all lines as a Stream-----!>");
            lines.forEach(System.out :: println);
            lines.close();
        } catch(IOException io) {
            io.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException, ClassNotFoundException {
        trainAndWrite("ner-model.ser.gz",
                "/Users/volodymyr/IdeaProjects/pii-ner-service/src/main/resources/ner.properties",
                "/Users/volodymyr/IdeaProjects/pii-ner-service/src/main/resources/regexner.txt");
        CRFClassifier classifier = getModel("ner-model.ser.gz");


        Supplier<Stream<String>> lines = () -> {
            try {
                return Files.lines(Paths.get("/Users/volodymyr/IdeaProjects/pii-ner-service/src/main/resources/data"));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        };

        lines.get().forEach(line -> System.out.println(classifier.classifyToString(line)));
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();

        lines.get().map(line -> {
            List<Triple<String, Integer, Integer>> offsets = classifier.classifyToCharacterOffsets(line);

            List<Triple<String, Integer, Integer>> rules = offsets.stream()
                    .filter(l -> l.first.equalsIgnoreCase("person"))
                    .collect(Collectors.toList());
            String tmpLine = line;
            for (Triple<String, Integer, Integer> lineOffset : rules) {
                tmpLine = new StringBuilder(tmpLine).replace(lineOffset.second, lineOffset.third,
                               repeat(lineOffset.third - lineOffset.second))
                        .toString();
            }
            return tmpLine;

        }).forEach(System.out::println);

    }

    public static String repeat(int n) {
        return String.join("", Collections.nCopies(n, "}"));
    }

}
