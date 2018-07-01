import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.cli.*;

public class Main {
    /**
     *
     * @param args
     * @args[0] = Path to database
     * @args[1] = Floorplan ID
     * @args[2] = output file name for the JSON encoded features cache
     */
    public static void main(String args[]) {
        Options options = new Options();

        Option dbPath = new Option("d", "database", true, "SQLite Database File Path");
        dbPath.setRequired(true);
        options.addOption(dbPath);

        Option fpIdOption = new Option("f", "floorplanid", true, "Floorplan ID");
        fpIdOption.setRequired(true);
        options.addOption(fpIdOption);

        Option outFileOption = new Option("o", "outputfile", true, "Output file");
        outFileOption.setRequired(true);
        options.addOption(outFileOption);

        Option interpolateOption = new Option("i", "interpolate", true, "Should use interpolation");
        interpolateOption.setRequired(true);
        options.addOption(interpolateOption);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);

            String dbFileName = cmd.getOptionValue("database");
            String fpId = cmd.getOptionValue("floorplanid");
            String outputFileName = cmd.getOptionValue("outputfile");
            boolean interpolate = cmd.getOptionValue("interpolate").toLowerCase().equals("true");

            Db db = new Db(dbFileName);
            String cache = db.createFeaturesCache(fpId, interpolate);


            File newTextFile = new File(outputFileName);
            FileWriter fw = null;
            try {
                fw = new FileWriter(newTextFile);
                fw.write(cache);
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }catch(ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);

            System.exit(1);
        }


    }
}
