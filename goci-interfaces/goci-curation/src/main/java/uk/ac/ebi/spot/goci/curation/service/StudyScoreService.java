package uk.ac.ebi.spot.goci.curation.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import uk.ac.ebi.spot.goci.model.Study;
import uk.ac.ebi.spot.goci.repository.StudyRepository;
import uk.ac.ebi.spot.goci.service.StudyService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by xinhe on 27/06/2017.
 */
@Controller
@RequestMapping("/studyscore")
public class StudyScoreService {
    private StudyRepository studyRepository;
    private StudyService studyService;

    public StudyScoreService(StudyRepository studyRepository, StudyService studyService) {
        this.studyRepository = studyRepository;
        this.studyService = studyService;
    }

    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public Map<String, Map<String,String>> prepareStudiesScore() {


        Collection<Study> studies = studyRepository.findByHousekeepingCatalogPublishDateIsNullOrHousekeepingCatalogUnpublishDateIsNotNull();
        studies = studies.stream()
                .filter(study -> {
                    System.out.print(study.getId());
                    System.out.print('\n');
                    if(study.getId()==7864){
                        System.out.print(study.getId());
                    }
                    if(study.getHousekeeping().getCurationStatus() != null){
                        if (study.getHousekeeping().getCurationStatus().getStatus().equals("Curation Abandoned")  ||
                                study.getHousekeeping().getCurationStatus().getStatus().equals("CNV Paper")){
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
        //        Page<Study> studyPage = studyRepository.findAll( new PageRequest(1, 10));
        Map<String, Map<String,String>> scoreFeatures = new HashMap<String, Map<String,String>>();
//        studyPage.getContent().forEach(study -> {
        studies.forEach(study -> {
            System.out.print(study.getId());
            System.out.print("\n");
            if(study.getId() == 14856835){
                System.out.print(study.getId());
            }
            Map<String, String> studyScoreFactors = new HashMap<String, String>();
            studyScoreFactors.put("InitialSampleSize","0");
            studyScoreFactors.put("ReplicateSampleSize","0");
            studyScoreFactors.put("ReplicationStageIncluded","false");
            study.getAncestries().forEach(ancestry -> {
                switch(ancestry.getType()){
                    case "initial":
                        if(ancestry.getNumberOfIndividuals()!=null){
                            studyScoreFactors.put("InitialSampleSize",ancestry.getNumberOfIndividuals().toString());
                        }
                        break;
                    case "replication":
                        if(ancestry.getNumberOfIndividuals()!=null){
                            studyScoreFactors.put("ReplicateSampleSize",ancestry.getNumberOfIndividuals().toString());
                            studyScoreFactors.put("ReplicationStageIncluded","true");
                        }
                        break;
                    default:
                        ;
                }
            });

            studyScoreFactors.put("Publication",study.getPublication());
            studyScoreFactors.put("SummaryStatisticsAvailable",study.getFullPvalueSet().toString());
            studyScoreFactors.put("PublicationDate",study.getPublicationDate().toString());
            studyScoreFactors.put("GenomeWideCoverage","1");
            studyScoreFactors.put("UserRequested",study.getUserRequested().toString());
            studyScoreFactors.put("Trait", study.getEfoTraits().stream().map(efoTrait -> {
                return efoTrait.getTrait().toString();
            }).collect(Collectors.joining(",")));


            studyScoreFactors.put("Author",study.getAuthor().toString());
            studyScoreFactors.put("PubmedId",study.getPubmedId().toString());
            scoreFeatures.put(study.getId().toString(),studyScoreFactors);
        });

//        model.addAttribute("scoreFeatures", scoreFeatures);


        return scoreFeatures;
    }



    @RequestMapping(value = "/{studyId}", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public String prepareStudyScore(@PathVariable Long studyId) {

        Study study = studyRepository.findOne(studyId);



        return "{\"success\":1}";
    }


}