package uk.ac.ebi.spot.goci.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.spot.goci.model.CatalogHeaderBinding;
import uk.ac.ebi.spot.goci.model.CatalogHeaderBindings;
import uk.ac.ebi.spot.goci.repository.exception.DataImportException;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 20/02/15
 */
@Repository
public class CatalogImportRepository {

    private JdbcTemplate jdbcTemplate;

    private SimpleJdbcInsert insertStudyReport;

    private SimpleJdbcInsert insertAssociationReport;

    private SimpleJdbcInsert insertRegion;

    private SimpleJdbcInsert insertSnpRegion;

    private SimpleJdbcInsert insertGene;

    private SimpleJdbcInsert insertGenomicContext;

    private static final String SELECT_STUDY_REPORTS =
            "SELECT ID FROM STUDY_REPORT WHERE STUDY_ID = ?";

    private static final String UPDATE_STUDY_REPORTS =
            "UPDATE STUDY_REPORT SET " +
                    "PUBMED_ID_ERROR = ?, " +
                    "NCBI_PAPER_TITLE = ?, " +
                    "NCBI_FIRST_AUTHOR = ?, " +
                    "NCBI_NORMALIZED_FIRST_AUTHOR = ? " +
                    "WHERE ID = ?";

    private static final String SELECT_ASSOCIATION_REPORTS =
            "SELECT ID FROM ASSOCIATION_REPORT WHERE ASSOCIATION_ID = ?";

    private static final String UPDATE_ASSOCIATION_REPORTS =
            "UPDATE ASSOCIATION_REPORT SET " +
                    "LAST_UPDATE_DATE = ?, " +
                    "GENE_ERROR = ?, " +
                    "SNP_ERROR = ?, " +
                    "SNP_GENE_ON_DIFF_CHR = ?, " +
                    "NO_GENE_FOR_SYMBOL = ?, " +
                    "GENE_NOT_ON_GENOME = ?" +
                    "WHERE ID = ?";

    private static final String SELECT_HOUSEKEEPING = "SELECT HOUSEKEEPING_ID FROM STUDY WHERE ID = ?";

    private static final String SELECT_STATUS = "SELECT ID FROM CURATION_STATUS WHERE STATUS =? ";

    private static final String SELECT_CURRENT_STATUS_ID = "SELECT CURATION_STATUS_ID FROM HOUSEKEEPING WHERE ID =? ";

    private static final String SELECT_CURRENT_STATUS = "SELECT STATUS FROM CURATION_STATUS WHERE ID =? ";

    private static final String UPDATE_HOUSEKEEPING =
            "UPDATE HOUSEKEEPING SET CURATION_STATUS_ID = ?, LAST_UPDATE_DATE = ? WHERE ID = ?";

    private static final String SELECT_CATALOG_PUBLISH_DATE =
            "SELECT CATALOG_PUBLISH_DATE FROM HOUSEKEEPING WHERE ID =?";

    private static final String UPDATE_CATALOG_PUBLISH_DATE =
            "UPDATE HOUSEKEEPING SET CATALOG_PUBLISH_DATE = ? WHERE ID = ?";

    private static final String SELECT_SNP = "SELECT ID FROM SINGLE_NUCLEOTIDE_POLYMORPHISM WHERE RS_ID = ?";

    private static final String SELECT_REGION =
            "SELECT ID FROM REGION WHERE NAME = ?";

    private static final String SELECT_SNP_REGION =
            "SELECT SNP_ID FROM SNP_REGION WHERE REGION_ID = ?";

    private static final String SELECT_SNP_ID_FROM_SNP_REGION = "SELECT SNP_ID FROM SNP_REGION WHERE SNP_ID = ?";

    private static final String UPDATE_SNP_REGION = "UPDATE SNP_REGION SET REGION_ID =? WHERE SNP_ID = ?";

    private static final String UPDATE_SNP =
            "UPDATE SINGLE_NUCLEOTIDE_POLYMORPHISM " +
                    "SET CHROMOSOME_NAME = ?, " +
                    "CHROMOSOME_POSITION =?," +
                    "MERGED = ?, " +
                    "FUNCTIONAL_CLASS = ?, " +
                    "LAST_UPDATE_DATE = ? " +
                    "WHERE ID =?";

    private static final String SELECT_GENE = "SELECT ID FROM GENE WHERE GENE_NAME = ?";

    private static final String SELECT_SNP_ID_FROM_GENOMIC_CONTEXT =
            "SELECT SNP_ID FROM GENOMIC_CONTEXT WHERE GENE_ID = ?";

    private static final String UPDATE_GENOMIC_CONTEXT = "UPDATE GENOMIC_CONTEXT " +
            "SET IS_UPSTREAM=?, IS_DOWNSTREAM=?, DISTANCE=?, IS_INTERGENIC=? " +
            "WHERE GENE_ID = ? AND SNP_ID = ?";

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @Autowired
    public CatalogImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        this.insertStudyReport =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("STUDY_REPORT")
                        .usingColumns("STUDY_ID",
                                      "PUBMED_ID_ERROR",
                                      "NCBI_PAPER_TITLE",
                                      "NCBI_FIRST_AUTHOR",
                                      "NCBI_NORMALIZED_FIRST_AUTHOR",
                                      "NCBI_FIRST_UPDATE_DATE")
                        .usingGeneratedKeyColumns("ID");


        this.insertAssociationReport =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("ASSOCIATION_REPORT")
                        .usingColumns("ASSOCIATION_ID",
                                      "LAST_UPDATE_DATE",
                                      "GENE_ERROR",
                                      "SNP_ERROR",
                                      "SNP_GENE_ON_DIFF_CHR",
                                      "NO_GENE_FOR_SYMBOL",
                                      "GENE_NOT_ON_GENOME",
                                      "SNP_PENDING")
                        .usingGeneratedKeyColumns("ID");

        this.insertRegion = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("REGION")
                .usingColumns("NAME")
                .usingGeneratedKeyColumns("ID");

        this.insertSnpRegion = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("SNP_REGION")
                .usingColumns("SNP_ID", "REGION_ID");

        this.insertGene = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("GENE")
                .usingColumns("GENE_NAME", "ENTREZ_GENE_ID")
                .usingGeneratedKeyColumns("ID");

        this.insertGenomicContext = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("GENOMIC_CONTEXT")
                .usingColumns("SNP_ID", "GENE_ID", "IS_UPSTREAM", "IS_DOWNSTREAM", "DISTANCE", "IS_INTERGENIC")
                .usingGeneratedKeyColumns("ID");
    }

    public void loadNCBIMappedData(String[][] data) {

        // Create a map of col number to header
        Map<Integer, String> colNumHeaderMap = new HashMap<>();
        Integer colNum = 0;

        for (String[] header : data) {
            for (String cell : header) {
                colNumHeaderMap.put(colNum, cell);
                colNum++;
            }
            // Break after first line, as this is all we need to establish header
            break;
        }

        Map<CatalogHeaderBinding, Integer> headersToExtract = mapHeader(colNumHeaderMap);
        mapData(headersToExtract, extractRange(data, 1));
    }

    private Map<CatalogHeaderBinding, Integer> mapHeader(Map<Integer, String> colNumHeaderMap) {
        Map<CatalogHeaderBinding, Integer> result = new HashMap<>();
        for (int i : colNumHeaderMap.keySet()) {
            for (CatalogHeaderBinding binding : CatalogHeaderBindings.getLoadHeaders()) {
                if (binding.getLoadInclusion().columnName().get().toUpperCase().equals(colNumHeaderMap.get(i).toUpperCase())) {
                    result.put(binding, i);
                    break;
                }
            }
        }
        return result;
    }

    private void mapData(Map<CatalogHeaderBinding, Integer> headerColumnMap, String[][] data) {
        // 2014-08-01 00:00:00.000
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        // Read through each line
        int row = 0;
        boolean caughtReadErrors = false;
        boolean caughtWriteErrors = false;

        // This map store information on whether study or its associations have an error
        Map<Long, List<Boolean>> studyErrorMap = new HashMap<>();
        Map<Long, List<Boolean>> importErrorMap = new HashMap<>();
        for (String[] line : data) {

            row++;
            List<Boolean> importErrors = new ArrayList<>();

            // Study report attributes
            Long studyId = null; // STUDY_ID
            Integer pubmedIdError = null;  // PUBMED_ID_ERROR
            String ncbiPaperTitle = null; // NCBI_PAPER_TITLE
            String ncbiFirstAuthor = null; // NCBI_FIRST_AUTHOR
            String ncbiNormalisedFirstAuthor = null; // NCBI_NORMALIZED_FIRST_AUTHOR
            Date ncbiFirstUpdateDate = null; // NCBI_FIRST_UPDATE_DATE

            // Association report attributes
            Long associationId = null; // ASSOCIATION_ID
            Date lastUpdateDate = null; // LAST_UPDATE_DATE
            Integer geneError = null; // GENE_ERROR
            String snpError = null; // SNP_ERROR
            String snpGeneOnDiffChr = null; // SNP_GENE_ON_DIFF_CHR
            String noGeneForSymbol = null; // NO_GENE_FOR_SYMBOL
            String geneNotOnGenome = null; // GENE_NOT_ON_GENOME

            // Mapped genetic info
            String region = null; // REGION
            String chromosomeName = null; // CHROMOSOME_NAME
            String chromosomePosition = null; // CHROMOSOME_POSITION
            String upstreamMappedGene = null; // UPSTREAM_MAPPED_GENE
            String upstreamEntrezGeneId = null; // UPSTREAM_ENTREZ_GENE_ID
            Integer upstreamGeneDistance = null; // UPSTREAM_GENE_DISTANCE
            String downstreamMappedGene = null; // DOWNSTREAM_MAPPED_GENE
            String downstreamEntrezGeneId = null; // DOWNSTREAM_ENTREZ_GENE_ID
            Integer downstreamGeneDistance = null; // DOWNSTREAM_GENE_DISTANCE
            Integer isIntergenic = null; // IS_INTERGENIC
            String snpId = null; // SNP_ID
            Integer merged = null; // MERGED
            String mappedGene = null; // MAPPED_GENE
            String entrezGeneId = null; // ENTREZ_GENE_ID
            String functionalClass = null; // FUNCTIONAL_CLASS

            // For each key in our map, extract the cell at that index
            for (CatalogHeaderBinding binding : headerColumnMap.keySet()) {
                try {
                    String valueToInsert = line[headerColumnMap.get(binding)].trim();

                    switch (binding) {
                        case STUDY_ID:
                            if (valueToInsert.isEmpty()) {
                                studyId = null;
                            }
                            else {
                                studyId = Long.valueOf(valueToInsert);
                            }
                            break;
                        case PUBMED_ID_ERROR:
                            if (valueToInsert.isEmpty()) {
                                pubmedIdError = null;
                            }
                            else {
                                pubmedIdError = Integer.valueOf(valueToInsert);
                            }
                            break;
                        case NCBI_PAPER_TITLE:
                            if (valueToInsert.isEmpty()) {
                                ncbiPaperTitle = null;
                            }
                            else {
                                ncbiPaperTitle = valueToInsert;
                            }
                            break;
                        case NCBI_FIRST_AUTHOR:
                            if (valueToInsert.isEmpty()) {
                                ncbiFirstAuthor = null;
                            }
                            else {
                                ncbiFirstAuthor = valueToInsert;
                            }
                            break;
                        case NCBI_NORMALISED_FIRST_AUTHOR:
                            if (valueToInsert.isEmpty()) {
                                ncbiNormalisedFirstAuthor = null;
                            }
                            else {
                                ncbiNormalisedFirstAuthor = valueToInsert;
                            }
                            break;
                        case ASSOCIATION_ID:
                            if (valueToInsert.isEmpty()) {
                                associationId = null;
                            }
                            else {
                                associationId = Long.valueOf(valueToInsert);
                            }
                            break;
                        case GENE_ERROR:
                            if (valueToInsert.isEmpty()) {
                                geneError = null;
                            }
                            else {
                                geneError = Integer.valueOf(valueToInsert);
                            }
                            break;
                        case SNP_ERROR:
                            if (valueToInsert.isEmpty()) {
                                snpError = null;
                            }
                            else {
                                snpError = valueToInsert;
                            }
                            break;
                        case SNP_GENE_ON_DIFF_CHR:
                            if (valueToInsert.isEmpty()) {
                                snpGeneOnDiffChr = null;
                            }
                            else {
                                snpGeneOnDiffChr = valueToInsert;
                            }
                            break;
                        case NO_GENE_FOR_SYMBOL:
                            if (valueToInsert.isEmpty()) {
                                noGeneForSymbol = null;
                            }
                            else {
                                noGeneForSymbol = valueToInsert;
                            }
                            break;
                        case GENE_NOT_ON_GENOME:
                            if (valueToInsert.isEmpty()) {
                                geneNotOnGenome = null;
                            }
                            else {
                                geneNotOnGenome = valueToInsert;
                            }
                            break;
                        case REGION:
                            if (valueToInsert.isEmpty()) {
                                region = null;
                            }
                            else {
                                region = valueToInsert;
                            }
                            break;
                        case CHROMOSOME_NAME:
                            if (valueToInsert.isEmpty()) {
                                chromosomeName = null;
                            }
                            else {
                                chromosomeName = valueToInsert;
                            }
                            break;
                        case CHROMOSOME_POSITION:
                            if (valueToInsert.isEmpty()) {
                                chromosomePosition = null;
                            }
                            else {
                                chromosomePosition = valueToInsert;
                            }
                            break;
                        case ENTREZ_UPSTREAM_MAPPED_GENE:
                            if (valueToInsert.isEmpty()) {
                                upstreamMappedGene = null;
                            }
                            else {
                                upstreamMappedGene = valueToInsert;
                            }
                            break;
                        case ENTREZ_UPSTREAM_GENE_ID:
                            if (valueToInsert.isEmpty()) {
                                upstreamEntrezGeneId = null;
                            }
                            else {
                                upstreamEntrezGeneId = valueToInsert;
                            }
                            break;
                        case ENTREZ_UPSTREAM_GENE_DISTANCE:
                            if (valueToInsert.isEmpty()) {
                                upstreamGeneDistance = null;
                            }
                            else {
                                upstreamGeneDistance = Integer.valueOf(valueToInsert);
                            }
                            break;
                        case ENTREZ_DOWNSTREAM_MAPPED_GENE:
                            if (valueToInsert.isEmpty()) {
                                downstreamMappedGene = null;
                            }
                            else {
                                downstreamMappedGene = valueToInsert;
                            }
                            break;
                        case ENSEMBL_DOWNSTREAM_GENE_ID:
                            if (valueToInsert.isEmpty()) {
                                downstreamEntrezGeneId = null;
                            }
                            else {
                                downstreamEntrezGeneId = valueToInsert;
                            }
                            break;
                        case ENTREZ_DOWNSTREAM_GENE_DISTANCE:
                            if (valueToInsert.isEmpty()) {
                                downstreamGeneDistance = null;
                            }
                            else {
                                downstreamGeneDistance = Integer.valueOf(valueToInsert);
                            }
                            break;
                        case IS_INTERGENIC_ENTREZ:
                            if (valueToInsert.isEmpty()) {
                                isIntergenic = null;
                            }
                            else {
                                isIntergenic = Integer.valueOf(valueToInsert);
                            }
                            break;
                        case SNP_ID:
                            if (valueToInsert.isEmpty()) {
                                snpId = null;
                            }
                            else {
                                snpId = valueToInsert;
                            }
                            break;
                        case MERGED:
                            if (valueToInsert.isEmpty()) {
                                merged = null;
                            }
                            else {
                                merged = Integer.valueOf(valueToInsert);
                            }
                            break;
                        case ENTREZ_MAPPED_GENE:
                            if (valueToInsert.isEmpty()) {
                                mappedGene = null;
                            }
                            else {
                                mappedGene = valueToInsert;
                            }
                            break;
                        case ENTREZ_MAPPED_GENE_ID:
                            if (valueToInsert.isEmpty()) {
                                entrezGeneId = null;
                            }
                            else {
                                entrezGeneId = valueToInsert;
                            }
                            break;
                        case FUNCTIONAL_CLASS:
                            if (valueToInsert.isEmpty()) {
                                functionalClass = null;
                            }
                            else {
                                functionalClass = valueToInsert;
                            }
                            break;
                        default:
                            throw new DataImportException(
                                    "Unrecognised column flagged for import: " + binding.getLoadInclusion().columnName().get());
                    }

                }

                catch (Exception e) {
                    getLog().error("Unable to read data at row " + row + ", from spreadsheet", e);
                    caughtReadErrors = true;

                    if (importErrorMap.containsKey(studyId)) {
                        importErrors = importErrorMap.get(studyId);
                    }
                    importErrors.add(true);
                    importErrorMap.put(studyId, importErrors);

                }
            }

            // If no errors for a row, insert
            if (!caughtReadErrors) {

                try {
                    // Record information on study errors which will use to update status
                    List<Boolean> errorStatus = new ArrayList<>();
                    if (studyId != null && pubmedIdError != null) {
                        if (studyErrorMap.containsKey(studyId)) {
                            errorStatus = studyErrorMap.get(studyId);
                        }
                        errorStatus.add(true);
                    }
                    // Only report these errors where we have an association id
                    else if (associationId != null && (geneError != null || snpError != null)) {
                        if (studyErrorMap.containsKey(studyId)) {
                            errorStatus = studyErrorMap.get(studyId);
                        }
                        errorStatus.add(true);
                    }
                    // No error found
                    else {
                        if (studyErrorMap.containsKey(studyId)) {
                            errorStatus = studyErrorMap.get(studyId);
                        }
                        errorStatus.add(false);
                    }
                    studyErrorMap.put(studyId, errorStatus);

                    // Add study report
                    addStudyReport(studyId,
                                   pubmedIdError,
                                   ncbiPaperTitle,
                                   ncbiFirstAuthor,
                                   ncbiNormalisedFirstAuthor,
                                   ncbiFirstUpdateDate);

                    // Add association report if file returns an association ID
                    if (associationId != null) {
                        addAssociationReport(associationId,
                                             lastUpdateDate,
                                             geneError,
                                             snpError,
                                             snpGeneOnDiffChr,
                                             noGeneForSymbol,
                                             geneNotOnGenome);
                    }
                    else {
                        getLog().warn(
                                "Row in file with no association id, not adding association report for study: " +
                                        studyId);
                    }

                    // Add mapped data
                    addMappedData(snpError,
                                  region,
                                  chromosomeName,
                                  chromosomePosition,
                                  upstreamMappedGene,
                                  upstreamEntrezGeneId,
                                  upstreamGeneDistance,
                                  downstreamMappedGene,
                                  downstreamEntrezGeneId,
                                  downstreamGeneDistance,
                                  isIntergenic,
                                  snpId,
                                  merged,
                                  mappedGene,
                                  entrezGeneId,
                                  functionalClass);


                }
                catch (Exception e) {
                    getLog().error("Unable to write data at row " + row + ", from spreadsheet", e);
                    caughtWriteErrors = true;
                    if (importErrorMap.containsKey(studyId)) {
                        importErrors = importErrorMap.get(studyId);
                    }
                    importErrors.add(true);
                    importErrorMap.put(studyId, importErrors);
                }
            }
        }

        // For each study in map we update the status
        updateStudyStatus(studyErrorMap, importErrorMap);

        if (caughtReadErrors || caughtWriteErrors) {
            throw new DataImportException("Caught errors whilst processing data import - " +
                                                  "please check the logs for more information");
        }


    }

    private void addStudyReport(Long studyId,
                                Integer pubmedIdError,
                                String ncbiPaperTitle,
                                String ncbiFirstAuthor,
                                String ncbiNormalisedFirstAuthor,
                                Date ncbiFirstUpdateDate) {

        if (studyId == null) {
            throw new DataImportException("Caught errors processing data import - " +
                                                  "trying to add study report with no study ID");
        }

        if (ncbiPaperTitle == null) {
            throw new DataImportException("Caught errors processing data import - " +
                                                  "trying to add study report with no paper title");
        }

        // Set NCBI First Update date
        ncbiFirstUpdateDate = new Date();

        // Check for an existing id in database
        int rows;
        try {

            // Don't update ncbiFirstUpdateDate as that is set on initial insert
            Long studyReportId = jdbcTemplate.queryForObject(SELECT_STUDY_REPORTS, Long.class, studyId);
            rows = jdbcTemplate.update(UPDATE_STUDY_REPORTS,
                                       pubmedIdError,
                                       ncbiPaperTitle,
                                       ncbiFirstAuthor,
                                       ncbiNormalisedFirstAuthor,
                                       studyReportId);
        }
        // If not in database add a new report
        catch (EmptyResultDataAccessException e) {
            Map<String, Object> studyReportArgs = new HashMap<>();
            studyReportArgs.put("STUDY_ID", studyId);
            studyReportArgs.put("PUBMED_ID_ERROR", pubmedIdError);
            studyReportArgs.put("NCBI_PAPER_TITLE", ncbiPaperTitle);
            studyReportArgs.put("NCBI_FIRST_AUTHOR", ncbiFirstAuthor);
            studyReportArgs.put("NCBI_NORMALIZED_FIRST_AUTHOR", ncbiNormalisedFirstAuthor);
            studyReportArgs.put("NCBI_FIRST_UPDATE_DATE", ncbiFirstUpdateDate);
            rows = insertStudyReport.execute(studyReportArgs);
        }
        getLog().info("Adding report for Study ID: " + studyId + " - Updated " + rows + " rows");

    }


    private void addAssociationReport(Long associationId,
                                      Date lastUpdateDate,
                                      Integer geneError,
                                      String snpError,
                                      String snpGeneOnDiffChr,
                                      String noGeneForSymbol,
                                      String geneNotOnGenome) {

        if (associationId == null) {
            throw new DataImportException("Caught errors processing data import - " +
                                                  "trying to add association report with no association ID");
        }

        // This is not set in NCBI file, so set to current date
        lastUpdateDate = new Date();

        // Check for an existing id in database
        int rows;
        try {
            Long associationReportId =
                    jdbcTemplate.queryForObject(SELECT_ASSOCIATION_REPORTS, Long.class, associationId);
            rows = jdbcTemplate.update(UPDATE_ASSOCIATION_REPORTS,
                                       lastUpdateDate,
                                       geneError,
                                       snpError,
                                       snpGeneOnDiffChr,
                                       noGeneForSymbol,
                                       geneNotOnGenome,
                                       associationReportId);
        }
        // If not in database add a new report
        catch (EmptyResultDataAccessException e) {
            Map<String, Object> associationReportArgs = new HashMap<>();
            associationReportArgs.put("ASSOCIATION_ID", associationId);
            associationReportArgs.put("LAST_UPDATE_DATE", lastUpdateDate);
            associationReportArgs.put("GENE_ERROR", geneError);
            associationReportArgs.put("SNP_ERROR", snpError);
            associationReportArgs.put("SNP_GENE_ON_DIFF_CHR", snpGeneOnDiffChr);
            associationReportArgs.put("NO_GENE_FOR_SYMBOL", noGeneForSymbol);
            associationReportArgs.put("GENE_NOT_ON_GENOME", geneNotOnGenome);
            associationReportArgs.put("SNP_PENDING", null);
            rows = insertAssociationReport.execute(associationReportArgs);
        }
        getLog().info("Adding report for Association ID: " + associationId + " - Updated " + rows + " rows");

    }

    private void addMappedData(String snpError,
                               String region,
                               String chromosomeName,
                               String chromosomePosition,
                               String upstreamMappedGene,
                               String upstreamEntrezGeneId,
                               Integer upstreamGeneDistance,
                               String downstreamMappedGene,
                               String downstreamEntrezGeneId,
                               Integer downstreamGeneDistance,
                               Integer isIntergenic,
                               String snpId,
                               Integer merged,
                               String mappedGene,
                               String entrezGeneId,
                               String functionalClass) {


        // Do not attempt to add mapped data for entries with snp errors
        // These tend to come back with just limited region information
        if (snpError != null && !snpError.isEmpty()) {
            getLog().info("Not adding mapped data for snpId: rs" + snpId + " , with error " + snpError);
            return;
        }

        // The SNP identifier in the file returned from NCBI is the rsId minus the rs at beginning e.g. 55734731
        // Get the ID of SNP in database,
        // Note: the snp should already be in database otherwise it would never have appeared in file sent to NCBI
        String rsId = "rs" + snpId;
        Long snpIdInSnpTable;
        List<Long> snpIdsInDatabase;
        try {
            snpIdInSnpTable = jdbcTemplate.queryForObject(SELECT_SNP, Long.class, rsId);
        }

        catch (EmptyResultDataAccessException e) {
            throw new DataImportException("Caught errors processing data import - " +
                                                  "trying to add NCBI info for SNP " + rsId +
                                                  ", but RSID not found in database");
        }    // Catch case where we have more than one snp with that RSID
        catch (IncorrectResultSizeDataAccessException e) {
            snpIdsInDatabase = jdbcTemplate.query(SELECT_SNP,
                                                  (resultSet, i) -> resultSet.getLong("ID"), rsId);
            snpIdInSnpTable = snpIdsInDatabase.get(0);

        }

        // Add region information
        Long regionId;
        if (region != null) {
            try {
                regionId = jdbcTemplate.queryForObject(SELECT_REGION, Long.class, region);
            }
            catch (EmptyResultDataAccessException e) {
                // Insert region if its not already in database
                createRegion(region);

                // Get the ID of the newly created region
                regionId = jdbcTemplate.queryForObject(SELECT_REGION, Long.class, region);
            }

            // Create link in SNP_REGION table
            if (regionId != null) {
                Collection<Long> snpIdsInSnpRegionTable =
                        jdbcTemplate.queryForList(SELECT_SNP_REGION, Long.class, regionId);

                // Examine all SNPs linked to region and if no link exists then create
                if (!snpIdsInSnpRegionTable.contains(snpIdInSnpTable)) {
                    createSnpRegion(snpIdInSnpTable, regionId);
                }
            }
        }

        // Add chromosome name, chromosome position, functional class, merged and last update date values to SNP table
        // Last update date is not set in NCBI file, so set to current date
        Date lastUpdateDate = new Date();
        int snpRows = jdbcTemplate.update(UPDATE_SNP,
                                          chromosomeName,
                                          chromosomePosition,
                                          merged,
                                          functionalClass,
                                          lastUpdateDate,
                                          snpIdInSnpTable);
        getLog().info(
                "Adding chromosome name, chromosome position, merged, functional class and last update date values to SNP: " +
                        snpIdInSnpTable + " - Updated " + snpRows + " rows");

        // Add gene information to database
        Long geneId = null;
        List<Long> geneIdsFromDatabase = new ArrayList<Long>();

        // Process each mapped gene, can either be a single gene or ; separated string of gene names
        // This gene information comes from the snp_gene_symbols and snp_gene_ids columns
        if (mappedGene != null && !mappedGene.isEmpty()) {
            List<String> mappedGenes = new ArrayList<String>();
            if (mappedGene.contains(";")) {
                mappedGenes = Arrays.asList(mappedGene.split(";"));
            }
            else {
                mappedGenes.add(mappedGene);
            }

            List<String> entrezGeneIds = new ArrayList<String>();
            if (entrezGeneId.contains(";")) {
                entrezGeneIds = Arrays.asList(entrezGeneId.split(";"));
            }
            else {
                entrezGeneIds.add(entrezGeneId);
            }

            // Iterators
            Iterator mappedGenesIterator = mappedGenes.iterator();
            Iterator entrezGeneIdsIterator = entrezGeneIds.iterator();

            while (mappedGenesIterator.hasNext() && entrezGeneIdsIterator.hasNext()) {

                // Establish values in iterators
                String geneNameItr = mappedGenesIterator.next().toString();
                String entrezGeneIdItr = entrezGeneIdsIterator.next().toString();

                try {
                    geneId = jdbcTemplate.queryForObject(SELECT_GENE, Long.class, geneNameItr);
                }
                catch (EmptyResultDataAccessException e) {
                    // Insert gene if its not already in database
                    createGene(geneNameItr, entrezGeneIdItr);
                    geneId = jdbcTemplate.queryForObject(SELECT_GENE, Long.class, geneNameItr);
                }
                // Catch case where we have more than one gene
                catch (IncorrectResultSizeDataAccessException e) {
                    geneIdsFromDatabase = jdbcTemplate.query(SELECT_GENE,
                                                             (resultSet, i) -> resultSet.getLong("ID"), geneNameItr);
                    geneId = geneIdsFromDatabase.get(0);

                }


                // Check GENOMIC_CONTEXT table to see if SNP has entry in this table for this gene
                List snpIdsInGenomicContextTable =
                        jdbcTemplate.queryForList(SELECT_SNP_ID_FROM_GENOMIC_CONTEXT, Long.class, geneId);

                if (!snpIdsInGenomicContextTable.contains(snpIdInSnpTable)) {
                    // Create genomic context, for mapped genes there is no details in file for following attributes...
                    createGenomicContext(geneId, snpIdInSnpTable, false, false, null, isIntergenic);
                }
                else {
                    // Update
                    int genomicContextRows = jdbcTemplate.update(UPDATE_GENOMIC_CONTEXT,
                                                                 false,
                                                                 false,
                                                                 null,
                                                                 isIntergenic,
                                                                 geneId,
                                                                 snpIdInSnpTable);
                    getLog().info(
                            "Updating genomic context for SNP ID: " +
                                    snpIdInSnpTable + " and GENE ID:" + geneId + " - Updated " +
                                    genomicContextRows + " rows");

                }
            }// end while
        }

        // Process upstream gene information, there is always only one
        if (upstreamMappedGene != null && !upstreamMappedGene.isEmpty()) {
            try {
                geneId = jdbcTemplate.queryForObject(SELECT_GENE, Long.class, upstreamMappedGene);
            }
            catch (EmptyResultDataAccessException e) {
                createGene(upstreamMappedGene, upstreamEntrezGeneId);
                geneId = jdbcTemplate.queryForObject(SELECT_GENE, Long.class, upstreamMappedGene);
            }   // Catch case where we have more than one gene
            catch (IncorrectResultSizeDataAccessException e) {
                geneIdsFromDatabase = jdbcTemplate.query(SELECT_GENE,
                                                         (resultSet, i) -> resultSet.getLong("ID"), upstreamMappedGene);
                geneId = geneIdsFromDatabase.get(0);

            }

            // Check GENOMIC_CONTEXT table to see if SNP has entry in this table for this gene
            List snpIdsInGenomicContextTable =
                    jdbcTemplate.queryForList(SELECT_SNP_ID_FROM_GENOMIC_CONTEXT, Long.class, geneId);


            // If its a new gene, never seen in database, then create genomic context
            if (!snpIdsInGenomicContextTable.contains(snpIdInSnpTable)) {
                createGenomicContext(geneId, snpIdInSnpTable, true, false, upstreamGeneDistance, isIntergenic);
            }

            else {
                int genomicContextRows =
                        jdbcTemplate.update(UPDATE_GENOMIC_CONTEXT,
                                            true,
                                            false,
                                            upstreamGeneDistance,
                                            isIntergenic,
                                            geneId,
                                            snpIdInSnpTable);
                getLog().trace(
                        "Updating genomic context for SNP ID: " +
                                snpIdInSnpTable + " and GENE ID:" + geneId + " - Updated " +
                                genomicContextRows + " rows");

            }
        }

        // Process downstream gene information, there is always only one
        if (downstreamMappedGene != null && !downstreamMappedGene.isEmpty()) {
            try {
                geneId = jdbcTemplate.queryForObject(SELECT_GENE, Long.class, downstreamMappedGene);
            }
            catch (EmptyResultDataAccessException e) {
                createGene(downstreamMappedGene, downstreamEntrezGeneId);
                geneId = jdbcTemplate.queryForObject(SELECT_GENE, Long.class, downstreamMappedGene);
            }
            catch (IncorrectResultSizeDataAccessException e) {
                geneIdsFromDatabase = jdbcTemplate.query(SELECT_GENE,
                                                         (resultSet, i) -> resultSet.getLong("ID"),
                                                         downstreamMappedGene);
                geneId = geneIdsFromDatabase.get(0);
            }
            // Check GENOMIC_CONTEXT table to see if SNP has entry in this table for this gene
            List snpIdsInGenomicContextTable =
                    jdbcTemplate.queryForList(SELECT_SNP_ID_FROM_GENOMIC_CONTEXT, Long.class, geneId);


            // If its a new gene, never seen in database, then create genomic context
            if (!snpIdsInGenomicContextTable.contains(snpIdInSnpTable)) {
                createGenomicContext(geneId, snpIdInSnpTable, false, true, downstreamGeneDistance, isIntergenic);
            }
            else {

                int genomicContextRows =
                        jdbcTemplate.update(UPDATE_GENOMIC_CONTEXT,
                                            false,
                                            true,
                                            downstreamGeneDistance,
                                            isIntergenic,
                                            geneId,
                                            snpIdInSnpTable);
                getLog().trace(
                        "Updating genomic context for SNP ID: " +
                                snpIdInSnpTable + " and GENE ID:" + geneId + " - Updated " +
                                genomicContextRows + " rows");

            }
        }
    }


    private void updateStudyStatus(Map<Long, List<Boolean>> studyErrorMap, Map<Long, List<Boolean>> importErrorMap) {

        for (Long studyId : studyErrorMap.keySet()) {

            // Get study housekeeping ID
            Long housekeepingId;
            try {
                housekeepingId = jdbcTemplate.queryForObject(SELECT_HOUSEKEEPING, Long.class, studyId);
            }
            catch (EmptyResultDataAccessException e) {
                throw new DataImportException("Caught errors processing data import - " +
                                                      "trying to update status of study without housekeeping information found in database");
            }


            // Get current status
            String currentStatus = null;
            Long currentStatusId;
            try {
                currentStatusId = jdbcTemplate.queryForObject(SELECT_CURRENT_STATUS_ID, Long.class, housekeepingId);
            }
            catch (EmptyResultDataAccessException e) {
                throw new DataImportException("Caught errors processing data import - " +
                                                      "trying to update status of study without status information found in database");
            }

            if (currentStatusId != null) {
                currentStatus = jdbcTemplate.queryForObject(SELECT_CURRENT_STATUS, String.class, currentStatusId);
            }

            // Check for errors
            List<Boolean> errorStatus = studyErrorMap.get(studyId);
            List<Boolean> importErrorStatus = importErrorMap.get(studyId);
            String status;

            // Study has an error
            if (errorStatus != null && errorStatus.contains(true)) {
                status = "NCBI pipeline error";
            }
            // Import detected an error
            else if (importErrorStatus != null && importErrorStatus.contains(true)) {
                status = "Data import error";
            }
            // No errors found for study
            else {
                status = "Publish study";
            }

            // Get status ID
            Long statusId;
            try {
                statusId = jdbcTemplate.queryForObject(SELECT_STATUS, Long.class, status);
            }
            catch (EmptyResultDataAccessException e) {
                throw new DataImportException(
                        "Caught errors processing data import - cannot find ID for status " + status);
            }

            Date currentPublishDate = null;
            // Get publish date
            try {
                currentPublishDate = jdbcTemplate.queryForObject(SELECT_CATALOG_PUBLISH_DATE, Date.class, housekeepingId);
            }
            catch (EmptyResultDataAccessException e) {
                throw new DataImportException("Caught errors processing data import - " +
                                                      "trying to update status of study without publish date information found in database");
            }

            // Only update the status if the curators if previous status was "Send to NCBI"
            if (currentStatus.equalsIgnoreCase("Send to NCBI")) {
                // Set status and last_update_date
                Date lastUpdateDate = new Date();

                int rows = 0;
                rows = jdbcTemplate.update(UPDATE_HOUSEKEEPING, statusId, lastUpdateDate, housekeepingId);

                if (status.equals("Publish study")) {

                    //Also update publish date if one doesn't exist
                    if (currentPublishDate == null) {
                        Date publishDate = new Date();
                        jdbcTemplate.update(UPDATE_CATALOG_PUBLISH_DATE, publishDate, housekeepingId);
                    }
                }
                getLog().info(
                        "Updated housekeeping information for study: " + studyId + " - Updated " + rows +
                                " rows");
            }


        }
    }


    private void createRegion(String region) {
        Map<String, Object> regionArgs = new HashMap<>();
        regionArgs.put("NAME", region);

        int rows = 0;
        rows = insertRegion.execute(regionArgs);
        getLog().trace(
                "Adding region: " + region + " - Updated " +
                        rows + " rows");
    }

    private void createSnpRegion(Long snpId, Long regionId) {

        Map<String, Object> snpRegionArgs = new HashMap<>();
        snpRegionArgs.put("SNP_ID", snpId);
        snpRegionArgs.put("REGION_ID", regionId);
        int rows = 0;

        List<Long>existingSnpIdsInSnpRegionTable = new ArrayList<>();

        // Need to check if a SNP already has a region
        try {
            existingSnpIdsInSnpRegionTable =
                    jdbcTemplate.queryForList(SELECT_SNP_ID_FROM_SNP_REGION, Long.class, snpId);
        }
        // If we don't get a result then insert the link between region and snp
        catch (EmptyResultDataAccessException e) {
            getLog().info(
                    "SNP: " + snpId + " has no existing region");
        }

        if (existingSnpIdsInSnpRegionTable != null && !existingSnpIdsInSnpRegionTable.isEmpty()) {
            jdbcTemplate.update(UPDATE_SNP_REGION, regionId, snpId);
            getLog().info(
                    "Updating SNP: " + snpId + ", setting region to " + regionId + " - Updated " + rows +
                            " rows");
        }
        else{
            // Insert if its not already in database
            rows = insertSnpRegion.execute(snpRegionArgs);
            getLog().info(
                    "Adding SNP: " + snpId + " and Region: " + regionId + " - Updated " + rows +
                            " rows");

        }
    }

    private void createGenomicContext(Long geneId,
                                      Long snpIdInSnpTable,
                                      Boolean isUpstream,
                                      Boolean isDownstream,
                                      Integer distance, Integer isIntergenic) {

        Map<String, Object> genomicContextArgs = new HashMap<>();
        genomicContextArgs.put("SNP_ID", snpIdInSnpTable);
        genomicContextArgs.put("GENE_ID", geneId);
        genomicContextArgs.put("IS_UPSTREAM", isUpstream);
        genomicContextArgs.put("IS_DOWNSTREAM", isDownstream);
        genomicContextArgs.put("DISTANCE", distance);
        genomicContextArgs.put("IS_INTERGENIC", isIntergenic);

        int rows = 0;
        rows = insertGenomicContext.execute(genomicContextArgs);
        getLog().trace(
                "Adding genomic context information for gene id: " + geneId + ", linked to snp" + snpIdInSnpTable +
                        " - Updated " +
                        rows + " rows");
    }

    private void createGene(String geneName, String entrezGeneId) {
        Map<String, Object> geneArgs = new HashMap<>();
        geneArgs.put("GENE_NAME", geneName);
        geneArgs.put("ENTREZ_GENE_ID", entrezGeneId);

        int rows = 0;
        rows = insertGene.execute(geneArgs);
        getLog().trace(
                "Adding gene information for gene id: " + geneName + ", entrez id" + entrezGeneId +
                        " - Updated " +
                        rows + " rows");
    }

    private static <T> T[] extractRange(T[] array, int startIndex) {
        if (startIndex > array.length) {
            return (T[]) Array.newInstance(array.getClass().getComponentType(), 0);
        }
        else {
            T[] response = (T[]) Array.newInstance(
                    array.getClass().getComponentType(),
                    array.length - startIndex);

            System.arraycopy(array, startIndex, response, 0, response.length);
            return response;
        }
    }


}