package com.cyt.common.jw.service.impl.jx;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


import com.cyt.common.jw.dao.aware.jx.CoursePlanDaoAware;
import com.cyt.common.jw.dao.aware.jx.EduScheduleDaoAware;
import com.cyt.common.jw.model.po.jx.CourseTeacher;
import com.cyt.common.jw.model.po.jx.EduSchedule;
import com.cyt.common.jw.model.po.jx.EduXn;
import com.cyt.common.jw.model.po.jx.EduXq;
import com.cyt.common.jw.model.queryinfo.jx.CoursePlanQueryInfo;
import com.cyt.common.jw.model.queryinfo.jx.ScheduleQueryInfo;
import com.cyt.common.jw.service.aware.jx.CoursePlanServiceAware;
import com.cyt.common.jw.service.aware.jx.EduGradeServiceAware;
import com.cyt.common.jw.service.aware.jx.ScheduleServiceAware;
import com.cyt.common.jw.service.aware.jx.XnXqServiceAware;
import com.cyt.common.system.helper.EduConstants;
import com.cyt.common.system.model.po.Code;
import com.cyt.common.system.model.po.EduClass;
import com.cyt.common.system.model.po.EduGrade;
import com.cyt.common.system.model.po.EduStaff;
import com.cyt.common.system.model.po.UserInfo;
import com.cyt.common.util.Constants;
import com.inxite.core.service.impl.BaseServiceImpl;
import com.inxite.core.util.StringUtil;


public class ScheduleServiceImpl extends BaseServiceImpl implements ScheduleServiceAware {


    private CoursePlanServiceAware coursePlanService;
    private XnXqServiceAware xnXqService;
    private EduGradeServiceAware eduGradeService;


    private CoursePlanDaoAware coursePlanDao;
    private EduScheduleDaoAware eduScheduleDao;


    private static final String STR_FORMAT = "00";
    DecimalFormat df = new DecimalFormat(STR_FORMAT);
    private static final int DAY_COUNT = 7;
    private static final int LECTURE_COUNT = 8;
    private int advConf = 0;
    private int cCConf = 0;


    // --------------------------------------------测试用数据START------------------------------------------------------------


    // ----------------------------------------------END---------------------------------------------------------------------


    public Boolean scheduleAlgorithm(List<EduGrade> scheduleGrade, Map<EduGrade, int[][]> mapGradeHaveCourseTime,
            Map<EduStaff, String> groupStaffCourseTime, Map<EduGrade, Map<String, Integer>> gradeCoupletCourse,
            int tmpadvConf, int tmpcCConf, UserInfo userInfo) {


        // ------------------准备基础数据---------------------------------
        advConf = tmpadvConf;
        cCConf = tmpcCConf;
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        // 年级ID-年级 映射 & 年级ID List & 班级列表
        Map<String, EduGrade> gradeAndGradeId = new HashMap<String, EduGrade>();
        String[] gradeIdList = new String[scheduleGrade.size()];
        List<EduClass> scheduleClass = new ArrayList<EduClass>();
        for (int i = 0; i < scheduleGrade.size(); i++) {
            gradeAndGradeId.put(scheduleGrade.get(i).getGradeId(), scheduleGrade.get(i));
            gradeIdList[i] = scheduleGrade.get(i).getGradeId();
            scheduleClass.addAll(scheduleGrade.get(i).getClassList());
        }
        // 课程List
        List<Code> courseList = listCourse(userInfo);
        // 年级－课程－多少班有MAP
        Map<EduGrade, Map<String, Integer>> classHasCourse = getGradeClassHasClassNum(gradeIdList, userInfo);
        // 年级ID List 查询条件
        CoursePlanQueryInfo queryInfo = new CoursePlanQueryInfo();
        queryInfo.setRemoveTag(0);
        queryInfo.setGradeIdList(gradeIdList);
        queryInfo.setXnId(xnXqService.getCurrentXn(userInfo.getSchoolId()).getXnId());
        queryInfo.setXqId(xnXqService.getCurrentXq(userInfo.getSchoolId()).getXqId());
        // 根据排课年级获得之前设置的班级课程教师信息
        List<CourseTeacher> scheduleJoint = coursePlanService.listCourseTeacher(queryInfo);
        // 获得教师信息
        List<EduStaff> scheduleStaff = coursePlanService.listTeacher(queryInfo);
        // 教师-所带班级映射
        Map<EduStaff, List<EduClass>> teacherClass = new HashMap<EduStaff, List<EduClass>>();
        for (int i = 0; i < scheduleStaff.size(); i++) {
            CoursePlanQueryInfo tempqueryInfo = new CoursePlanQueryInfo();
            tempqueryInfo.setStaffId(scheduleStaff.get(i).getStaffId());
            tempqueryInfo.setSchoolId(userInfo.getSchoolId());
            List<EduClass> thisTeacherClass = coursePlanService.listTeacherClass(tempqueryInfo);
            teacherClass.put(scheduleStaff.get(i), thisTeacherClass);
        }
        // 教师人数
        int teacherNum = scheduleStaff.size();
        // 理想得分
        int idealScore = teacherNum + 1;
        // 班级-班级ID映射
        Map<String, EduClass> classAndClassId = new HashMap<String, EduClass>();
        for (int i = 0; i < scheduleClass.size(); i++) {
            classAndClassId.put(scheduleClass.get(i).getClassId(), scheduleClass.get(i));
        }
        // 建立班级排课参数映射
        Map<EduClass, ClassScheduleConf> scheduleConf = new HashMap<EduClass, ClassScheduleConf>();
        for (int i = 0; i < scheduleGrade.size(); i++) {
            List<EduClass> tempGradeClassList = scheduleGrade.get(i).getClassList();
            for (int j = 0; j < tempGradeClassList.size(); j++) {
                EduClass thisClass = tempGradeClassList.get(j);
                ClassScheduleConf tempScheduleConf = new ClassScheduleConf();
                queryInfo.setClassId(thisClass.getClassId());
                // 获取该班级下课程
                List<CourseTeacher> thisClassCoursePlan = coursePlanService.listCourseTeacher(queryInfo);
                Map<Integer, Integer> thisCourseTime = new HashMap<Integer, Integer>();
                for (int k = 0; k < thisClassCoursePlan.size(); k++) {
                    thisCourseTime.put(thisClassCoursePlan.get(k).getCoursePlan().getCourseCode(), thisClassCoursePlan


                    .get(k).getCoursePlan().getZhxs());
                }
                tempScheduleConf.courseTime = thisCourseTime;
                tempScheduleConf.haveClass = mapGradeHaveCourseTime.get(scheduleGrade.get(i));
                // 班级课程池映射
                List<String> coursePool = new ArrayList<String>();
                // 初始化课程池计数
                int coursePoolCount = 0;
                // 向课程池中添加课程
                for (int courseCount = 0; courseCount < thisClassCoursePlan.size(); courseCount++) {
                    for (int m = 0; m < thisCourseTime.get(thisClassCoursePlan.get(courseCount).getCoursePlan()
                            .getCourseCode()); m++) {
                        coursePool.add(coursePoolCount,
                                df.format(thisClassCoursePlan.get(courseCount).getCoursePlan().getCourseCode()));
                        coursePoolCount++;
                    }
                }
                tempScheduleConf.coursePool = coursePool;
                scheduleConf.put(thisClass, tempScheduleConf);
            }
        }
        // -------------------高级排课------------------------------


        List<EduStaff> specialTeacher = new ArrayList<EduStaff>();
        specialTeacher.addAll(scheduleStaff);
        // 获取scheduleStaff与（groupStaffCourseTime的key）的交集，即既在教研组，又有排课安排的教师
        List<EduStaff> staffWithConf = new ArrayList<EduStaff>(groupStaffCourseTime.keySet());
        specialTeacher.retainAll(staffWithConf);
        // 理想得分
        int advTotalScore = specialTeacher.size() + 1;
        if (advConf == 1) {
            idealScore = idealScore + advTotalScore;
        }


        // ------------------联堂课--------------------------------
        if (cCConf == 1) {
            int ccScore = 1;
            for (int i = 0; i < scheduleGrade.size(); i++) {
                Map<String, Integer> idealCC = gradeCoupletCourse.get(scheduleGrade.get(i));
                Iterator<Map.Entry<String, Integer>> idealCCEntries = idealCC.entrySet().iterator();
                while (idealCCEntries.hasNext()) {
                    idealCCEntries.next();
                    ccScore = ccScore + 1;
                }
            }
            idealScore = idealScore + ccScore;
        }


        // ------------------新建种群------------------------------
        List<Individual> population = new ArrayList<Individual>();


        // 向种群中加入100个初始个体
        for (int individualNum = 0; individualNum < 100; individualNum++) {


            Individual indi = newindi(scheduleGrade, scheduleConf, scheduleJoint, scheduleStaff, specialTeacher,
                    groupStaffCourseTime, gradeAndGradeId, gradeCoupletCourse, courseList, classHasCourse);
            // 将个体置入种群
            population.add(individualNum, indi);
        }


        Individual finalindi = evolve(population, scheduleConf, scheduleClass, scheduleJoint, idealScore,
                scheduleStaff, gradeAndGradeId, classAndClassId, teacherClass, scheduleGrade, specialTeacher,
                groupStaffCourseTime, gradeCoupletCourse, courseList, classHasCourse);


        coursePlanDao.checkSchedule(scheduleGrade, eduXn, eduXq);
        saveSchedule(finalindi, userInfo, eduXn, eduXq, classAndClassId, scheduleJoint);


        return true;
    }


    public List<EduSchedule> listSchedule(ScheduleQueryInfo queryInfo, UserInfo userInfo) {
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        if (null != eduXn && null != eduXq) {
            queryInfo.setXnId(eduXn.getXnId());
            queryInfo.setXqId(eduXq.getXqId());
            queryInfo.setRemoveTag(Constants.REMOVE_NO);
            return eduScheduleDao.listEduSchedule(queryInfo);
        }
        else {
            return null;
        }


    }


    public List<EduSchedule> listFreeTeacher(ScheduleQueryInfo queryInfo, UserInfo userInfo) {
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        if (null != eduXn && null != eduXq) {
            queryInfo.setXnId(eduXn.getXnId());
            queryInfo.setXqId(eduXq.getXqId());
            queryInfo.setRemoveTag(Constants.REMOVE_NO);
            return eduScheduleDao.listFreeTeacher(queryInfo);
        }
        else {
            return null;
        }


    }


    public List<Code> listCourse(UserInfo userInfo) {
        ScheduleQueryInfo queryInfo = new ScheduleQueryInfo();
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        queryInfo.setRemoveTag(Constants.REMOVE_NO);
        return eduScheduleDao.listCourse(queryInfo);
    }


    public List<Code> listGradeCourse(UserInfo userInfo, ScheduleQueryInfo queryInfo) {
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        queryInfo.setRemoveTag(Constants.REMOVE_NO);
        return eduScheduleDao.listGradeCourse(queryInfo);
    }


    public List<EduStaff> listTeacher(CoursePlanQueryInfo queryInfo, UserInfo userInfo) {
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        queryInfo.setRemoveTag(Constants.REMOVE_NO);
        return coursePlanDao.listTeacher(queryInfo);
    }


    @Override
    public List<EduGrade> listScheduleGrade(UserInfo userInfo) {
        ScheduleQueryInfo queryInfo = new ScheduleQueryInfo();
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        List<EduGrade> eduGrade = eduScheduleDao.listScheduleGrade(queryInfo);
        int year = eduXn.getYear();
        for (int i = 0; i < eduGrade.size(); i++) {
            if (eduGrade.get(i).getPhrase() == EduConstants.GRADE_PHRASE_XX) {
                eduGrade.get(i).setGradeValue(year - eduGrade.get(i).getYear() + 1);
                eduGrade.get(i).setGradeName(EduConstants.GRADE_NAME_MAP.get(eduGrade.get(i).getGradeValue()));
            }
            else if (eduGrade.get(i).getPhrase() == EduConstants.GRADE_PHRASE_CZ) {
                eduGrade.get(i).setGradeValue(year - eduGrade.get(i).getYear() + 7);
                eduGrade.get(i).setGradeName(EduConstants.GRADE_NAME_MAP.get(eduGrade.get(i).getGradeValue()));
            }
            else if (eduGrade.get(i).getPhrase() == EduConstants.GRADE_PHRASE_GZ) {
                eduGrade.get(i).setGradeValue(year - eduGrade.get(i).getYear() + 10);
                eduGrade.get(i).setGradeName(EduConstants.GRADE_NAME_MAP.get(eduGrade.get(i).getGradeValue()));
            }
        }
        return eduGrade;
    }


    @Override
    public Map<EduClass, List<EduSchedule>> listGradeSchedule(ScheduleQueryInfo queryInfo, UserInfo userInfo) {
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        queryInfo.setRemoveTag(Constants.REMOVE_NO);
        List<EduClass> classList = eduGradeService.listClassByGrade(queryInfo.getGradeId());
        Map<EduClass, List<EduSchedule>> gradeScheduleList = new LinkedHashMap<EduClass, List<EduSchedule>>();
        for (int i = 0; i < classList.size(); i++) {
            queryInfo.setClassId(classList.get(i).getClassId());
            List<EduSchedule> scheduleList = eduScheduleDao.listEduSchedule(queryInfo);
            gradeScheduleList.put(classList.get(i), scheduleList);
        }
        return gradeScheduleList;
    }


    public Map<EduGrade, Integer> coursePlanCheck(CoursePlanQueryInfo queryInfo, UserInfo userInfo) {
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        queryInfo.setRemoveTag(Constants.REMOVE_NO);
        List<EduGrade> allGrade = eduGradeService.listGradeClassTree(userInfo.getSchoolId());
        Map<EduGrade, Integer> gradeCourseNum = new HashMap<EduGrade, Integer>();
        for (int i = 0; i < allGrade.size(); i++) {
            if (allGrade.get(i).getClassList() == null) {
                gradeCourseNum.put(allGrade.get(i), 0);
            }
            else {
                List<EduClass> classList = allGrade.get(i).getClassList();
                queryInfo.setClassId(classList.get(0).getClassId());
                int classCourseNum = coursePlanDao.gradeCourseNum(queryInfo);
                if (classList.size() != 1) {
                    for (int j = 1; j < classList.size(); j++) {
                        queryInfo.setClassId(classList.get(j).getClassId());
                        int classCourseNumTemp = coursePlanDao.gradeCourseNum(queryInfo);
                        if (classCourseNum != classCourseNumTemp) {
                            gradeCourseNum.put(allGrade.get(i), 0);
                        }
                    }


                }
                gradeCourseNum.put(allGrade.get(i), classCourseNum);
            }
        }
        return gradeCourseNum;
    }


    public Map<EduGrade, Map<String, Integer>> getGradeClassHasClassNum(String[] gradeIdList, UserInfo userInfo) {
        ScheduleQueryInfo queryInfo = new ScheduleQueryInfo();
        EduXn eduXn = xnXqService.getCurrentXn(userInfo.getSchoolId());
        EduXq eduXq = xnXqService.getCurrentXq(userInfo.getSchoolId());
        queryInfo.setXnId(eduXn.getXnId());
        queryInfo.setXqId(eduXq.getXqId());
        queryInfo.setRemoveTag(Constants.REMOVE_NO);
        Map<EduGrade, Map<String, Integer>> tmpMap = new HashMap<EduGrade, Map<String, Integer>>();
        for (int i = 0; i < gradeIdList.length; i++) {
            queryInfo.setGradeId(gradeIdList[i]);
            List<Code> gradeCourse = listGradeCourse(userInfo, queryInfo);
            Map<String, Integer> tmpCodeMap = new HashMap<String, Integer>();
            for (int j = 0; j < gradeCourse.size(); j++) {
                queryInfo.setCourseCode(gradeCourse.get(i).getCodeValue());
                int courseNum = eduScheduleDao.gradeCourseNum(queryInfo);
                tmpCodeMap.put(gradeCourse.get(j).getCodeValue().toString(), courseNum);
            }
            tmpMap.put((EduGrade) this.get(EduGrade.class, gradeIdList[i]), tmpCodeMap);
        }
        return tmpMap;
    }


    // 以下为排课私有方法---------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------------------------


    private Individual newindi(List<EduGrade> scheduleGrade, Map<EduClass, ClassScheduleConf> scheduleConf,
            List<CourseTeacher> scheduleJoint, List<EduStaff> scheduleStaff, List<EduStaff> specialTeacher,
            Map<EduStaff, String> groupStaffCourseTime, Map<String, EduGrade> gradeAndGradeId,
            Map<EduGrade, Map<String, Integer>> gradeCoupletCourse, List<Code> courseList,
            Map<EduGrade, Map<String, Integer>> classHasCourse) {
        // 新建个体
        Individual indi = new Individual();
        // 新建排课计划
        indi.schedulePlan = new GradeSchedule[scheduleGrade.size()];
        // 填充排课计划
        for (int gradeCount = 0; gradeCount < scheduleGrade.size(); gradeCount++) {
            // 新建年级计划
            GradeSchedule gradeSchedule = new GradeSchedule();
            // 获得年级
            EduGrade thisGrade = scheduleGrade.get(gradeCount);
            // 设置年级ID
            gradeSchedule.gradeId = thisGrade.getGradeId();
            // 获得年级下班级
            List<EduClass> thisClass = thisGrade.getClassList();
            // new班级计划数组
            gradeSchedule.classSchedule = new ClassSchedule[thisClass.size()];
            // 填充班级计划
            for (int classCount = 0; classCount < thisClass.size(); classCount++) {
                ClassSchedule classSchedule = new ClassSchedule();
                // 获得班级排课设置
                ClassScheduleConf thisScheduleConf = scheduleConf.get(thisClass.get(classCount));
                // 获得班级课程池
                List<String> thisCoursePool = thisScheduleConf.coursePool;
                // 设置课程池计数
                int coursePoolCount = 0;
                // 打乱课程池
                Collections.shuffle(thisCoursePool);
                // 按照 天-节 循环从打乱的课程池中获取课程，放入对象中
                for (int day = 0; day < DAY_COUNT; day++) {
                    DaySchedule daySchedule = new DaySchedule();
                    for (int lectureNum = 0; lectureNum < LECTURE_COUNT; lectureNum++) {
                        if (thisScheduleConf.haveClass[day][lectureNum] == 1) {
                            Schedule schedule = new Schedule();
                            schedule.courseId = thisCoursePool.get(coursePoolCount);
                            schedule.num = lectureNum;
                            daySchedule.schedule[lectureNum] = schedule;
                            classSchedule.code = classSchedule.code + thisCoursePool.get(coursePoolCount);
                            coursePoolCount++;
                        }
                    }
                    daySchedule.day = day;
                    classSchedule.daySchedule[day] = daySchedule;
                    classSchedule.classId = thisClass.get(classCount).getClassId();
                }
                gradeSchedule.classSchedule[classCount] = classSchedule;
                indi.schedulePlan[gradeCount] = gradeSchedule;
            }
        }
        // 计算适应度得分
        indi.score = calculateScore(indi.schedulePlan, scheduleJoint, scheduleStaff);
        if (advConf == 1) {
            indi.score = indi.score
                    + extraScore(indi.schedulePlan, scheduleJoint, specialTeacher, groupStaffCourseTime);
        }
        if (cCConf == 1) {
            indi.score = indi.score
                    + ccScore(indi.schedulePlan, gradeAndGradeId, gradeCoupletCourse, scheduleGrade, courseList,
                            classHasCourse);
        }
        return indi;
    }


    /**
     * 进化
     * 
     * @param population
     * @param scheduleConf
     * @param scheduleClass
     * @param scheduleJoint
     * @param idealScore
     * @param scheduleStaff
     * @param gradeAndGradeId
     * @param classAndClassId
     * @param teacherClass
     * @param scheduleGrade
     * @return
     */
    public Individual evolve(List<Individual> population, Map<EduClass, ClassScheduleConf> scheduleConf,
            List<EduClass> scheduleClass, List<CourseTeacher> scheduleJoint, int idealScore,
            List<EduStaff> scheduleStaff, Map<String, EduGrade> gradeAndGradeId, Map<String, EduClass> classAndClassId,
            Map<EduStaff, List<EduClass>> teacherClass, List<EduGrade> scheduleGrade, List<EduStaff> specialTeacher,
            Map<EduStaff, String> groupStaffCourseTime, Map<EduGrade, Map<String, Integer>> gradeCoupletCourse,
            List<Code> courseList, Map<EduGrade, Map<String, Integer>> classHasCourse) {
        // 终止标识
        int finalFlag = 0;
        // 终极个体位置
        int finalIndiNum = 0;
        // 迭代次数
        int iterationNum = 0;
        // 种群总得分
        int sumScore = 0;
        // 循环开始时间
        // long startTime = System.currentTimeMillis();
        // 循环最长时间
        // long limitTime = startTime + 300000;
        // 开始循环
        while (finalFlag != 1) {
            // 现在时间
            // long endTime = System.currentTimeMillis();
            // 若计算时间超过最长时间，则跳出
            // if (endTime > limitTime) {
            // return null;
            // }
            // 种群总得分置零
            sumScore = 0;
            // 迭代次数自增
            iterationNum++;
            System.out.print("第" + iterationNum + "次迭代");
            // 冲突检测/排除 冲突无法解决则淘汰个体，生成新随机个体替代
            for (int i = 0; i < 100; i++) {
                Individual tIndi = population.get(i);
                GradeSchedule[] newGS = conflictResolution(tIndi.schedulePlan, scheduleJoint, scheduleStaff,
                        teacherClass, classAndClassId, scheduleConf);
                // 若冲突解决，则用解决后的替换之
                if (newGS != null) {
                    tIndi.schedulePlan = newGS;
                }
                // 否则生成新的随机个体替换之
                else {


                    tIndi = newindi(scheduleGrade, scheduleConf, scheduleJoint, scheduleStaff, specialTeacher,
                            groupStaffCourseTime, gradeAndGradeId, gradeCoupletCourse, courseList, classHasCourse);
                }
                // =-----------------------
                if (advConf == 1) {
                    newGS = extraConflictResolution(tIndi.schedulePlan, scheduleJoint, scheduleStaff, specialTeacher,
                            groupStaffCourseTime, classAndClassId, teacherClass, scheduleConf);
                    // 若冲突解决，则用解决后的替换之
                    if (newGS != null) {
                        tIndi.schedulePlan = newGS;
                    }
                    // 否则生成新的随机个体替换之
                    else {
                        tIndi = newindi(scheduleGrade, scheduleConf, scheduleJoint, scheduleStaff, specialTeacher,
                                groupStaffCourseTime, gradeAndGradeId, gradeCoupletCourse, courseList, classHasCourse);
                    }
                }
                if (cCConf == 1) {
                    newGS = ccConflictResolution(tIndi.schedulePlan, gradeAndGradeId, gradeCoupletCourse,
                            scheduleGrade, classAndClassId, scheduleJoint, scheduleStaff, scheduleConf, courseList,
                            advConf, groupStaffCourseTime);
                    // 若冲突解决，则用解决后的替换之
                    if (newGS != null) {
                        tIndi.schedulePlan = newGS;
                    }
                    // 否则生成新的随机个体替换之
                    else {
                        tIndi = newindi(scheduleGrade, scheduleConf, scheduleJoint, scheduleStaff, specialTeacher,
                                groupStaffCourseTime, gradeAndGradeId, gradeCoupletCourse, courseList, classHasCourse);
                    }
                }
                // 计算适应度得分
                tIndi.score = calculateScore(tIndi.schedulePlan, scheduleJoint, scheduleStaff);
                if (advConf == 1) {
                    tIndi.score = tIndi.score
                            + extraScore(tIndi.schedulePlan, scheduleJoint, specialTeacher, groupStaffCourseTime);
                }
                if (cCConf == 1) {
                    tIndi.score = tIndi.score
                            + ccScore(tIndi.schedulePlan, gradeAndGradeId, gradeCoupletCourse, scheduleGrade,
                                    courseList, classHasCourse);
                }
                population.set(i, tIndi);
            }
            // 交配产生后代
            population = mating(population, 20, 1, scheduleConf, scheduleClass, scheduleJoint, idealScore,
                    scheduleStaff, gradeAndGradeId, classAndClassId, specialTeacher, groupStaffCourseTime,
                    gradeCoupletCourse, scheduleGrade, courseList, classHasCourse);
            // 变异
            population = mutation(population, (float) 0.005, scheduleClass, scheduleJoint, idealScore, scheduleStaff,
                    scheduleConf, gradeAndGradeId, classAndClassId, specialTeacher, groupStaffCourseTime,
                    gradeCoupletCourse, scheduleGrade, courseList, classHasCourse);
            // 判断个体是否达标
            for (int i = 0; i < 100; i++) {
                sumScore = sumScore + population.get(i).score;
                // 个体得分等于期望得分，则终止标识置为1
                if (population.get(i).score == idealScore) {
                    // 终止标识置为1
                    finalFlag = 1;
                    // 获得最终个体的编号
                    finalIndiNum = i;
                }


            }


            System.out.println(" 平均分：" + sumScore / 100);
        }


        Individual finalIndi = population.get(finalIndiNum);
        return finalIndi;
    }


    public Boolean saveSchedule(Individual indi, UserInfo userInfo, EduXn eduXn, EduXq eduXq,
            Map<String, EduClass> classAndClassId, List<CourseTeacher> scheduleJoint) {
        GradeSchedule[] gradeSchedule = indi.schedulePlan;
        List<EduSchedule> eduScheduleList = new ArrayList<EduSchedule>();
        // CoursePlanQueryInfo cpQueryInfo = new CoursePlanQueryInfo();
        for (int i = 0; i < gradeSchedule.length; i++) {
            GradeSchedule tmpGradeSchedule = gradeSchedule[i];
            ClassSchedule[] classSchedule = tmpGradeSchedule.classSchedule;
            for (int j = 0; j < classSchedule.length; j++) {
                ClassSchedule tmpClassSchedule = classSchedule[j];
                String classId = tmpClassSchedule.classId;
                DaySchedule[] daySchedule = tmpClassSchedule.daySchedule;
                for (int k = 0; k < daySchedule.length; k++) {
                    DaySchedule tmpDaySchedule = daySchedule[k];
                    int day = tmpDaySchedule.day;
                    Schedule[] schedule = tmpDaySchedule.schedule;
                    for (int m = 0; m < schedule.length; m++) {
                        Schedule tmpSchedule = schedule[m];
                        if (tmpSchedule != null) {
                            int course = Integer.parseInt(tmpSchedule.courseId);
                            int courseNum = tmpSchedule.num;
                            CourseTeacher tmpCourseTeacher = findCourseTeacherFromCTList(scheduleJoint, classId, course);
                            EduSchedule tmpEduSchedule = new EduSchedule();
                            tmpEduSchedule.setCourseTeacher(tmpCourseTeacher);
                            tmpEduSchedule.setXn(eduXn);
                            tmpEduSchedule.setXq(eduXq);
                            tmpEduSchedule.setEduClass(classAndClassId.get(classId));
                            tmpEduSchedule.setDay(day + 1);
                            tmpEduSchedule.setKskjs(courseNum + 1);
                            tmpEduSchedule.setJskjs(courseNum + 1);
                            tmpEduSchedule.setCreator(userInfo.getUserId());
                            eduScheduleList.add(tmpEduSchedule);
                        }
                    }
                }
            }
        }
        coursePlanDao.saveSchedule(eduScheduleList);
        return true;
    }


    /**
     * 交配（精英个体保留）
     * 
     * @date 2015-6-24
     * @return List<Individual>
     */
    private List<Individual> mating(List<Individual> population, int matingNum, int matingLength,
            Map<EduClass, ClassScheduleConf> scheduleConf, List<EduClass> scheduleClass,
            List<CourseTeacher> scheduleJoint, int idealScore, List<EduStaff> scheduleStaff,
            Map<String, EduGrade> gradeAndGradeId, Map<String, EduClass> classAndClassId,
            List<EduStaff> specialTeacher, Map<EduStaff, String> groupStaffCourseTime,
            Map<EduGrade, Map<String, Integer>> gradeCoupletCourse, List<EduGrade> scheduleGrade,
            List<Code> courseList, Map<EduGrade, Map<String, Integer>> classHasCourse) {


        // 获得精英个体
        Individual[] elite = new Individual[5];
        int eliteScore = 0;
        for (int i = 0; i < 100; i++) {
            if (population.get(i).score > eliteScore) {
                eliteScore = population.get(i).score;
                elite[0] = population.get(i);
            }
        }
        eliteScore = 0;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score > eliteScore) && (elite[0].score >= population.get(i).score)) {
                eliteScore = population.get(i).score;
                elite[1] = population.get(i);
            }
        }
        eliteScore = 0;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score > eliteScore) && (elite[1].score >= population.get(i).score)) {
                eliteScore = population.get(i).score;
                elite[2] = population.get(i);
            }
        }
        eliteScore = 0;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score > eliteScore) && (elite[2].score >= population.get(i).score)) {
                eliteScore = population.get(i).score;
                elite[3] = population.get(i);
            }
        }
        eliteScore = 0;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score > eliteScore) && (elite[3].score >= population.get(i).score)) {
                eliteScore = population.get(i).score;
                elite[4] = population.get(i);
            }
        }


        // 通过轮盘赌获得父母
        // 初始化轮盘赌轮盘
        int totalValue = 0;
        for (int i = 0; i < 100; i++) {
            totalValue = totalValue + population.get(i).score;
        }
        float[] disk = new float[100];
        for (int i = 0; i < 100; i++) {
            disk[i] = (float) (population.get(i).score) / (float) totalValue;
        }
        // 进行matingNum次轮盘赌
        List<Individual> selected = new ArrayList<Individual>();
        for (int i = 0; i < matingNum; i++) {
            Random rand = new Random();
            float sel = rand.nextFloat();
            float sumPs = 0;
            int j = 0;
            while (sumPs < sel) {
                sumPs = sumPs + disk[j];
                j = j + 1;
            }
            if (j > 99) {
                j = 99;
            }
            selected.add(i, population.get(j));
        }
        // 创建新出生个体List
        List<Individual> newBorn = new ArrayList<Individual>();
        // 对随机的某个年级进行matingLength长度的交配
        for (int i = 0; i < matingNum; i++) {
            Individual oldIndiA = new Individual();
            Individual oldIndiB = new Individual();
            if (i == matingNum - 1) {
                oldIndiA = selected.get(i);
                oldIndiB = selected.get(0);
            }
            else {
                oldIndiA = selected.get(i);
                oldIndiB = selected.get(i + 1);
            }
            Individual newIndi = new Individual();
            newIndi.schedulePlan = new GradeSchedule[oldIndiA.schedulePlan.length];
            Random gradeRand = new Random();
            int gradeRnum = gradeRand.nextInt(oldIndiA.schedulePlan.length);
            for (int gradeNum = 0; gradeNum < oldIndiA.schedulePlan.length; gradeNum++) {
                newIndi.schedulePlan[gradeNum] = new GradeSchedule();
                ClassSchedule[] scheduleA = oldIndiA.schedulePlan[gradeNum].classSchedule;
                ClassSchedule[] scheduleB = oldIndiB.schedulePlan[gradeNum].classSchedule;
                if (gradeNum == gradeRnum) {
                    Random rand = new Random();
                    int matingBegin = rand.nextInt(scheduleA.length + 1 - matingLength);
                    for (int j = matingBegin; j < (matingBegin + matingLength); j++) {
                        String tempClassScheduleCodeB = scheduleB[j].code;
                        scheduleA[j].code = tempClassScheduleCodeB;
                        EduClass tClasss = classAndClassId.get(scheduleA[j].classId);
                        scheduleA[j].daySchedule = getDayScheduleListFormGeneticCode(tempClassScheduleCodeB,
                                scheduleConf, tClasss);
                    }
                }
                newIndi.schedulePlan[gradeNum].gradeId = oldIndiA.schedulePlan[gradeNum].gradeId;
                newIndi.schedulePlan[gradeNum].classSchedule = scheduleA;
            }


            newIndi.score = calculateScore(newIndi.schedulePlan, scheduleJoint, scheduleStaff);
            if (advConf == 1) {
                newIndi.score = newIndi.score
                        + extraScore(newIndi.schedulePlan, scheduleJoint, specialTeacher, groupStaffCourseTime);
            }
            if (cCConf == 1) {
                newIndi.score = newIndi.score
                        + ccScore(newIndi.schedulePlan, gradeAndGradeId, gradeCoupletCourse, scheduleGrade, courseList,
                                classHasCourse);
            }
            newBorn.add(i, newIndi);
        }
        // 通过轮盘赌替换个体
        // 初始化轮盘赌轮盘
        totalValue = 0;
        for (int i = 0; i < 100; i++) {
            totalValue = totalValue + (idealScore - population.get(i).score);
        }
        float[] newDisk = new float[100];
        for (int i = 0; i < 100; i++) {
            newDisk[i] = (float) (idealScore - population.get(i).score) / (float) totalValue;
        }
        // 进行matingNum次轮盘赌
        int[] deleted = new int[matingNum];
        for (int i = 0; i < matingNum; i++) {
            Random rand = new Random();
            float sel = rand.nextFloat();
            float sumPs = 0;
            int j = 0;
            while (sumPs < sel) {
                sumPs = sumPs + newDisk[j];
                j = j + 1;
            }
            if (j > 99) {
                j = 99;
            }
            deleted[i] = j;
        }


        for (int i = 0; i < matingNum; i++) {
            population.set(deleted[i], newBorn.get(i));
        }


        // 获得分数最低个体序号
        int worestScore = 9999;
        int worestNum[] = new int[5];
        for (int i = 0; i < 100; i++) {
            if (population.get(i).score < worestScore) {
                worestScore = population.get(i).score;
                worestNum[0] = i;
            }
        }
        worestScore = 9999;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score < worestScore)
                    && (population.get(worestNum[0]).score <= population.get(i).score)) {
                worestScore = population.get(i).score;
                worestNum[1] = i;
            }
        }
        worestScore = 9999;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score < worestScore)
                    && (population.get(worestNum[1]).score <= population.get(i).score)) {
                worestScore = population.get(i).score;
                worestNum[2] = i;
            }
        }
        worestScore = 9999;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score < worestScore)
                    && (population.get(worestNum[2]).score <= population.get(i).score)) {
                worestScore = population.get(i).score;
                worestNum[3] = i;
            }
        }
        worestScore = 9999;
        for (int i = 0; i < 100; i++) {
            if ((population.get(i).score < worestScore)
                    && (population.get(worestNum[3]).score <= population.get(i).score)) {
                worestScore = population.get(i).score;
                worestNum[4] = i;
            }
        }
        // 用精英个体替换最差个体
        for (int i = 0; i < 5; i++) {
            population.set(worestNum[i], elite[i]);
        }
        return population;
    }


    /**
     * 种群变异
     * 
     * @date 2015-6-26
     * @param population
     * @param mutationPosibility
     * @param scheduleClass
     * @param courseCodeMap
     * @param scheduleJoint
     * @param idealScore
     * @return List<Individual>
     */
    private List<Individual> mutation(List<Individual> population, float mutationPosibility,
            List<EduClass> scheduleClass, List<CourseTeacher> scheduleJoint, int idealScore,
            List<EduStaff> scheduleStaff, Map<EduClass, ClassScheduleConf> scheduleConf,
            Map<String, EduGrade> gradeAndGradeId, Map<String, EduClass> classAndClassId,
            List<EduStaff> specialTeacher, Map<EduStaff, String> groupStaffCourseTime,
            Map<EduGrade, Map<String, Integer>> gradeCoupletCourse, List<EduGrade> scheduleGrade,
            List<Code> courseList, Map<EduGrade, Map<String, Integer>> classHasCourse) {


        for (int i = 0; i < 100; i++) {
            Individual tempIndividual = population.get(i);
            for (int gradeCount = 0; gradeCount < tempIndividual.schedulePlan.length; gradeCount++) {
                ClassSchedule[] tempGradeSchedule = tempIndividual.schedulePlan[gradeCount].classSchedule;
                Random rand = new Random();
                float posibility = rand.nextFloat();
                if (posibility < mutationPosibility) {
                    rand = new Random();
                    int classRnum = rand.nextInt(tempGradeSchedule.length);
                    EduClass tClass = classAndClassId.get(tempGradeSchedule[classRnum].classId);
                    int classCodeLength = tempGradeSchedule[classRnum].code.length();
                    int rnum1 = rand.nextInt(classCodeLength / 2) * 2;
                    int rnum2 = rand.nextInt(classCodeLength / 2) * 2;
                    String temp1 = tempGradeSchedule[classRnum].code.substring(rnum1, rnum1 + 2);
                    String temp2 = tempGradeSchedule[classRnum].code.substring(rnum2, rnum2 + 2);
                    if (rnum2 > rnum1) {
                        String str0_1 = tempGradeSchedule[classRnum].code.substring(0, rnum1);
                        String str1_2 = tempGradeSchedule[classRnum].code.substring(rnum1 + 2, rnum2);
                        String str2_3 = tempGradeSchedule[classRnum].code.substring(rnum2 + 2);
                        tempGradeSchedule[classRnum].code = str0_1 + temp2 + str1_2 + temp1 + str2_3;
                    }
                    else if (rnum1 > rnum2) {
                        String str0_1 = tempGradeSchedule[classRnum].code.substring(0, rnum2);
                        String str1_2 = tempGradeSchedule[classRnum].code.substring(rnum2 + 2, rnum1);
                        String str2_3 = tempGradeSchedule[classRnum].code.substring(rnum1 + 2);
                        tempGradeSchedule[classRnum].code = str0_1 + temp1 + str1_2 + temp2 + str2_3;
                    }


                    tempGradeSchedule[classRnum].daySchedule = getDayScheduleListFormGeneticCode(
                            tempGradeSchedule[classRnum].code, scheduleConf, tClass);
                    tempIndividual.schedulePlan[gradeCount].classSchedule = tempGradeSchedule;
                }
            }
            // 计算适应度得分
            tempIndividual.score = calculateScore(tempIndividual.schedulePlan, scheduleJoint, scheduleStaff);
            if (advConf == 1) {
                tempIndividual.score = tempIndividual.score
                        + extraScore(tempIndividual.schedulePlan, scheduleJoint, specialTeacher, groupStaffCourseTime);
            }
            if (cCConf == 1) {
                tempIndividual.score = tempIndividual.score
                        + ccScore(tempIndividual.schedulePlan, gradeAndGradeId, gradeCoupletCourse, scheduleGrade,
                                courseList, classHasCourse);
            }
            population.set(i, tempIndividual);
        }


        return population;
    }


    /**
     * 根据Code获取班级排课设置
     * 
     * @date 2015-7-2
     * @param geneticCode
     * @param courseCodeMap
     * @param scheduleConf
     * @param thisGrade
     * @return DaySchedule[]
     */
    private DaySchedule[] getDayScheduleListFormGeneticCode(String geneticCode,
            Map<EduClass, ClassScheduleConf> scheduleConf, EduClass thisClass) {


        // 获得年级排课设置
        ClassScheduleConf thisScheduleConf = scheduleConf.get(thisClass);
        DaySchedule[] tempDaySchedule = new DaySchedule[DAY_COUNT];
        int geneticCodeCount = 0;
        for (int day = 0; day < DAY_COUNT; day++) {
            DaySchedule daySchedule = new DaySchedule();
            for (int lectureNum = 0; lectureNum < LECTURE_COUNT; lectureNum++) {
                if (thisScheduleConf.haveClass[day][lectureNum] == 1) {
                    Schedule schedule = new Schedule();
                    String code = geneticCode.substring(geneticCodeCount, geneticCodeCount + 2);
                    schedule.courseId = code;
                    schedule.num = lectureNum;
                    daySchedule.schedule[lectureNum] = schedule;
                    geneticCodeCount = geneticCodeCount + 2;
                }
            }
            daySchedule.day = day;
            tempDaySchedule[day] = daySchedule;
        }
        return tempDaySchedule;
    }


    /**
     * 通过classId courseId 获取EduStaff
     * 
     * @date 2015-6-23
     * @param classId
     * @param courseId
     * @param scheduleJoint
     * @return EduStaff
     */
    private EduStaff getTempTeacherByClassIdCourseId(String classId, String courseId, List<CourseTeacher> scheduleJoint) {


        EduStaff tempTeacher = null;
        for (int i = 0; i < scheduleJoint.size(); i++) {
            if (StringUtil.compareValue(scheduleJoint.get(i).getEduClass().getClassId(), classId)
                    && StringUtil.compareValue(df.format(scheduleJoint.get(i).getCoursePlan().getCourseCode()),
                            courseId)) {
                tempTeacher = scheduleJoint.get(i).getStaff();
            }
        }
        return tempTeacher;
    }


    /**
     * 计算个体适应得分
     * 
     * @date 2015-6-23
     * @param individual
     * @return int
     */
    private int calculateScore(GradeSchedule[] gradeSchedule, List<CourseTeacher> scheduleJoint,
            List<EduStaff> scheduleStaff) {


        int totalScore = 1;
        // 判断教师是否重复上课


        // 统计教师每个时间段上课数
        Map<EduStaff, int[][]> staffDuplicateCourse = new HashMap<EduStaff, int[][]>();
        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {
            int[][] a = new int[DAY_COUNT][LECTURE_COUNT];
            for (int i = 0; i < DAY_COUNT; i++) {
                for (int j = 0; j < LECTURE_COUNT; j++) {
                    a[i][j] = 0;
                }
            }
            staffDuplicateCourse.put(scheduleStaff.get(staffNum), a);
        }
        GradeSchedule[] gS = gradeSchedule;
        for (int i = 0; i < gS.length; i++) {
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                            String classId = gS[i].classSchedule[j].classId;
                            String courseId = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            EduStaff tempTeacher = getTempTeacherByClassIdCourseId(classId, courseId, scheduleJoint);
                            int[][] temp = new int[DAY_COUNT][LECTURE_COUNT];
                            temp = staffDuplicateCourse.get(tempTeacher);
                            int templeteClassNum = temp[k][l];
                            temp[k][l] = templeteClassNum + 1;
                            staffDuplicateCourse.put(tempTeacher, temp);
                        }
                    }
                }
            }
        }


        // 初始化教师重复上课标志
        int teacherOverTeachFlag = 0;
        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {
            // 标志置零
            teacherOverTeachFlag = 0;
            int[][] a = staffDuplicateCourse.get(scheduleStaff.get(staffNum));
            for (int j = 0; j < DAY_COUNT; j++) {
                for (int k = 0; k < LECTURE_COUNT; k++) {
                    if (a[j][k] > 1) {
                        teacherOverTeachFlag = 1;
                    }
                }
            }
            if ((teacherOverTeachFlag == 0)) {
                totalScore = totalScore + 1;
            }
        }
        // if (totalScore >= 28) {
        // System.out.println(totalScore);
        // }
        return totalScore;
    }


    /**
     * 
     * 个体高级设置冲突检测排除
     * 
     * @date 2015-6-23
     * @param individual
     * @return int
     */
    private GradeSchedule[] extraConflictResolution(GradeSchedule[] gradeSchedule, List<CourseTeacher> scheduleJoint,


    List<EduStaff> scheduleStaff, List<EduStaff> specialTeacher, Map<EduStaff, String> groupStaffCourseTime,
            Map<String, EduClass> classAndClassId, Map<EduStaff, List<EduClass>> teacherClass,
            Map<EduClass, ClassScheduleConf> scheduleConf) {


        // 统计要求的教师每个时间段上课数
        Map<EduStaff, int[][]> staffDuplicateCourse = new HashMap<EduStaff, int[][]>();


        for (int staffNum = 0; staffNum < specialTeacher.size(); staffNum++) {
            int[][] a = new int[DAY_COUNT][LECTURE_COUNT];
            for (int i = 0; i < DAY_COUNT; i++) {
                for (int j = 0; j < LECTURE_COUNT; j++) {
                    a[i][j] = 0;
                }
            }
            staffDuplicateCourse.put(specialTeacher.get(staffNum), a);
        }
        GradeSchedule[] gS = gradeSchedule;
        for (int i = 0; i < gS.length; i++) {
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                            String classId = gS[i].classSchedule[j].classId;
                            String courseId = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            EduStaff tempTeacher = getTempTeacherByClassIdCourseId(classId, courseId, scheduleJoint);
                            if (specialTeacher.contains(tempTeacher)) {
                                int[][] temp = new int[DAY_COUNT][LECTURE_COUNT];
                                temp = staffDuplicateCourse.get(tempTeacher);
                                int templeteClassNum = temp[k][l];
                                temp[k][l] = templeteClassNum + 1;
                                staffDuplicateCourse.put(tempTeacher, temp);
                            }
                        }
                    }
                }
            }
        }
        // 统计所有教师每个时间段上课数
        Map<EduStaff, Map<String, List<EduClass>>> allStaffDuplicateCourse = new HashMap<EduStaff, Map<String, List<EduClass>>>();
        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {
            Map<String, List<EduClass>> tempMap = new HashMap<String, List<EduClass>>();
            for (int i = 0; i < DAY_COUNT; i++) {
                for (int j = 0; j < LECTURE_COUNT; j++) {
                    String tempString = i + "," + j;
                    tempMap.put(tempString, new ArrayList<EduClass>());
                }
            }
            allStaffDuplicateCourse.put(scheduleStaff.get(staffNum), tempMap);
        }
        for (int i = 0; i < gS.length; i++) {
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                            String classId = gS[i].classSchedule[j].classId;
                            String courseId = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            EduStaff TempTeacher = getTempTeacherByClassIdCourseId(classId, courseId, scheduleJoint);
                            Map<String, List<EduClass>> tempMap = allStaffDuplicateCourse.get(TempTeacher);
                            String key = k + "," + l;
                            List<EduClass> tempClassList = tempMap.get(key);
                            tempClassList.add(classAndClassId.get(classId));
                            tempMap.put(key, tempClassList);
                        }
                    }
                }
            }
        }


        // 获得每个时间段空闲教师
        Map<String, List<EduStaff>> freeTeacher = new HashMap<String, List<EduStaff>>();


        String[] keyList = new String[56];
        int count = 0;
        for (int i = 0; i < DAY_COUNT; i++) {
            for (int j = 0; j < LECTURE_COUNT; j++) {
                keyList[count] = i + "," + j;
                count++;
            }
        }


        for (int i = 0; i < keyList.length; i++) {
            freeTeacher.put(keyList[i], new ArrayList<EduStaff>());
        }


        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {


            Map<String, List<EduClass>> tempTeacherSchedule = allStaffDuplicateCourse.get(scheduleStaff.get(staffNum));


            for (int i = 0; i < keyList.length; i++) {
                if (tempTeacherSchedule.get(keyList[i]).size() == 0) {
                    List<EduStaff> tempTeacherList = freeTeacher.get(keyList[i]);
                    tempTeacherList.add(scheduleStaff.get(staffNum));
                    freeTeacher.put(keyList[i], tempTeacherList);
                }
            }


        }
        // 检测是否符合要求


        for (int i = 0; i < specialTeacher.size(); i++) {
            String conf = groupStaffCourseTime.get(specialTeacher.get(i));
            int dayConf = Integer.parseInt(conf.split("#")[0]);
            int timeSequence = Integer.parseInt(conf.split("#")[1]);
            int tmpSchedule[][] = staffDuplicateCourse.get(specialTeacher.get(i));
            if (timeSequence == 1) {
                for (int j = 0; j < 4; j++) {


                    if (tmpSchedule[dayConf - 1][j] != 0) {
                        // 获取这是哪个班的课
                        EduClass thisClass = ((allStaffDuplicateCourse.get(specialTeacher.get(i))).get((dayConf - 1)
                                + "," + Integer.toString(j))).get(0);
                        // 获取该冲突时间空闲的教师
                        List<EduStaff> thisFreeTeacher = freeTeacher.get((dayConf - 1) + "," + Integer.toString(j));
                        // 设置冲突解决flag
                        int doneFlag = 0;
                        // 逐个检测空闲教师所带班级是否有该班
                        for (int teacherCount = 0; teacherCount < thisFreeTeacher.size(); teacherCount++) {
                            if (doneFlag == 0) {
                                // 若为该班教师
                                if (teacherClass.get(thisFreeTeacher.get(teacherCount)).contains(thisClass)) {
                                    // 获取该教师上课安排Map
                                    Map<String, List<EduClass>> otherTeacherCourseList = allStaffDuplicateCourse
                                            .get(thisFreeTeacher.get(teacherCount));
                                    // 获取该教师该班级的上课安排
                                    List<String> otherTeacherSchedule = new ArrayList<String>();
                                    for (int keyListLength = 0; keyListLength < keyList.length; keyListLength++) {
                                        if (otherTeacherCourseList.get(keyList[keyListLength]).contains(thisClass)) {
                                            otherTeacherSchedule.add(keyList[keyListLength]);
                                        }
                                    }
                                    // 获得待调课程时间
                                    Random rand = new Random();
                                    int randCourseNum = rand.nextInt(otherTeacherSchedule.size());
                                    String[] newOrder = otherTeacherSchedule.get(randCourseNum).split(",");
                                    int day = Integer.parseInt(newOrder[0]);
                                    int courseNum = Integer.parseInt(newOrder[1]);
                                    // 获得冲突课程时间
                                    String[] oldOrder = { Integer.toString(dayConf - 1), Integer.toString(j) };
                                    int oldDay = Integer.parseInt(oldOrder[0]);
                                    int oldCourseNum = Integer.parseInt(oldOrder[1]);


                                    // 调换课程
                                    for (int gradeCount = 0; gradeCount < gS.length; gradeCount++) {
                                        if (StringUtil.compareValue(gS[gradeCount].gradeId, thisClass.getNj()
                                                .getGradeId())) {
                                            for (int classCount = 0; classCount < gS[gradeCount].classSchedule.length; classCount++) {
                                                if (StringUtil.compareValue(
                                                        gS[gradeCount].classSchedule[classCount].classId,
                                                        thisClass.getClassId())) {
                                                    // 待调课程courseId
                                                    String newCourseId = gS[gradeCount].classSchedule[classCount].daySchedule[day].schedule[courseNum].courseId;
                                                    // 冲突课程courseId
                                                    String oldCourseId = gS[gradeCount].classSchedule[classCount].daySchedule[oldDay].schedule[oldCourseNum].courseId;
                                                    // 对调
                                                    gS[gradeCount].classSchedule[classCount].daySchedule[day].schedule[courseNum].courseId = oldCourseId;
                                                    gS[gradeCount].classSchedule[classCount].daySchedule[oldDay].schedule[oldCourseNum].courseId = newCourseId;
                                                    gS[gradeCount].classSchedule[classCount].code = generateCode(
                                                            gS[gradeCount].classSchedule[classCount],
                                                            scheduleConf.get(thisClass));


                                                }
                                            }
                                        }
                                    }
                                    doneFlag = 1;
                                }
                            }
                        }
                        if (doneFlag == 0) {
                            return null;
                        }


                    }
                }
            }


            else if (timeSequence == 2) {
                for (int j = 4; j < 8; j++) {
                    if (tmpSchedule[dayConf - 1][j] != 0) {
                        // 获取这是哪个班的课
                        EduClass thisClass = ((allStaffDuplicateCourse.get(specialTeacher.get(i))).get((dayConf - 1)
                                + "," + Integer.toString(j))).get(0);
                        // 获取该冲突时间空闲的教师
                        List<EduStaff> thisFreeTeacher = freeTeacher.get((dayConf - 1) + "," + Integer.toString(j));
                        // 设置冲突解决flag
                        int doneFlag = 0;
                        // 逐个检测空闲教师所带班级是否有该班
                        for (int teacherCount = 0; teacherCount < thisFreeTeacher.size(); teacherCount++) {
                            if (doneFlag == 0) {
                                // 若为该班教师
                                if (teacherClass.get(thisFreeTeacher.get(teacherCount)).contains(thisClass)) {
                                    // 获取该教师上课安排Map
                                    Map<String, List<EduClass>> otherTeacherCourseList = allStaffDuplicateCourse
                                            .get(thisFreeTeacher.get(teacherCount));
                                    // 获取该教师该班级的上课安排
                                    List<String> otherTeacherSchedule = new ArrayList<String>();
                                    for (int keyListLength = 0; keyListLength < keyList.length; keyListLength++) {
                                        if (otherTeacherCourseList.get(keyList[keyListLength]).contains(thisClass)) {
                                            otherTeacherSchedule.add(keyList[keyListLength]);
                                        }
                                    }
                                    // 获得待调课程时间
                                    Random rand = new Random();
                                    int randCourseNum = rand.nextInt(otherTeacherSchedule.size());
                                    String[] newOrder = otherTeacherSchedule.get(randCourseNum).split(",");
                                    int day = Integer.parseInt(newOrder[0]);
                                    int courseNum = Integer.parseInt(newOrder[1]);
                                    // 获得冲突课程时间
                                    String[] oldOrder = { Integer.toString(dayConf - 1), Integer.toString(j) };
                                    int oldDay = Integer.parseInt(oldOrder[0]);
                                    int oldCourseNum = Integer.parseInt(oldOrder[1]);


                                    // 调换课程
                                    for (int gradeCount = 0; gradeCount < gS.length; gradeCount++) {
                                        if (StringUtil.compareValue(gS[gradeCount].gradeId, thisClass.getNj()
                                                .getGradeId())) {
                                            for (int classCount = 0; classCount < gS[gradeCount].classSchedule.length; classCount++) {
                                                if (StringUtil.compareValue(
                                                        gS[gradeCount].classSchedule[classCount].classId,
                                                        thisClass.getClassId())) {
                                                    // 待调课程courseId
                                                    String newCourseId = gS[gradeCount].classSchedule[classCount].daySchedule[day].schedule[courseNum].courseId;
                                                    // 冲突课程courseId
                                                    String oldCourseId = gS[gradeCount].classSchedule[classCount].daySchedule[oldDay].schedule[oldCourseNum].courseId;
                                                    // 对调
                                                    gS[gradeCount].classSchedule[classCount].daySchedule[day].schedule[courseNum].courseId = oldCourseId;
                                                    gS[gradeCount].classSchedule[classCount].daySchedule[oldDay].schedule[oldCourseNum].courseId = newCourseId;
                                                    gS[gradeCount].classSchedule[classCount].code = generateCode(
                                                            gS[gradeCount].classSchedule[classCount],
                                                            scheduleConf.get(thisClass));


                                                }
                                            }
                                        }
                                    }
                                    doneFlag = 1;
                                }
                            }
                        }
                        if (doneFlag == 0) {
                            return null;
                        }
                    }
                }
            }


        }


        return gS;
    }


    /**
     * 计算个体高级适应得分
     * 
     * @date 2015-6-23
     * @param individual
     * @return int
     */
    private int extraScore(GradeSchedule[] gradeSchedule, List<CourseTeacher> scheduleJoint,
            List<EduStaff> specialTeacher, Map<EduStaff, String> groupStaffCourseTime) {


        int totalScore = 1;


        // 统计要求的教师每个时间段上课数
        Map<EduStaff, int[][]> staffDuplicateCourse = new HashMap<EduStaff, int[][]>();
        for (int staffNum = 0; staffNum < specialTeacher.size(); staffNum++) {
            int[][] a = new int[DAY_COUNT][LECTURE_COUNT];
            for (int i = 0; i < DAY_COUNT; i++) {
                for (int j = 0; j < LECTURE_COUNT; j++) {
                    a[i][j] = 0;
                }
            }
            staffDuplicateCourse.put(specialTeacher.get(staffNum), a);
        }
        GradeSchedule[] gS = gradeSchedule;
        for (int i = 0; i < gS.length; i++) {
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                            String classId = gS[i].classSchedule[j].classId;
                            String courseId = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            EduStaff tempTeacher = getTempTeacherByClassIdCourseId(classId, courseId, scheduleJoint);
                            if (specialTeacher.contains(tempTeacher)) {
                                int[][] temp = new int[DAY_COUNT][LECTURE_COUNT];
                                temp = staffDuplicateCourse.get(tempTeacher);
                                int templeteClassNum = temp[k][l];
                                temp[k][l] = templeteClassNum + 1;
                                staffDuplicateCourse.put(tempTeacher, temp);
                            }
                        }
                    }
                }
            }
        }
        // 检测是否符合要求
        for (int i = 0; i < specialTeacher.size(); i++) {
            // 是否有课标志
            int hasCourseFlag = 0;
            String conf = groupStaffCourseTime.get(specialTeacher.get(i));
            int day = Integer.parseInt(conf.split("#")[0]);
            int timeSequence = Integer.parseInt(conf.split("#")[1]);
            int tmpSchedule[][] = staffDuplicateCourse.get(specialTeacher.get(i));
            if (timeSequence == 1) {
                for (int j = 0; j < 4; j++) {
                    if (tmpSchedule[day - 1][j] != 0) {
                        hasCourseFlag = 1;
                    }
                }
            }
            else if (timeSequence == 2) {
                for (int j = 4; j < 8; j++) {
                    if (tmpSchedule[day - 1][j] != 0) {
                        hasCourseFlag = 1;
                    }
                }
            }
            if (hasCourseFlag == 0) {
                totalScore = totalScore + 1;
            }
        }


        return totalScore;
    }


    /**
     * 计算联堂课适应得分
     * 
     * @date 2015-6-23
     * @param individual
     * @return int
     */
    private int ccScore(GradeSchedule[] gradeSchedule, Map<String, EduGrade> gradeAndGradeId,
            Map<EduGrade, Map<String, Integer>> gradeCoupletCourse, List<EduGrade> scheduleGrade,
            List<Code> courseList, Map<EduGrade, Map<String, Integer>> classHasCourse) {


        int totalScore = 1;


        // 计算每个年级每门课联堂课次数
        GradeSchedule[] gS = gradeSchedule;
        Map<EduGrade, Map<String, Integer>> tmpCCourseNum = new HashMap<EduGrade, Map<String, Integer>>();
        for (int i = 0; i < gS.length; i++) {
            Map<String, Integer> allClassCourseMap = new HashMap<String, Integer>();
            for (int courseCount = 0; courseCount < courseList.size(); courseCount++) {
                allClassCourseMap.put(courseList.get(courseCount).getCodeValue().toString(), 0);
            }
            EduGrade tmpGrade = gradeAndGradeId.get(gS[i].gradeId);
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                Map<String, Integer> tmpCourseMap = new HashMap<String, Integer>();
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (l == 0) {
                            String course2 = "";
                            String course3 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l + 1] != null) {
                                course3 = gS[i].classSchedule[j].daySchedule[k].schedule[l + 1].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course2, course3)) {
                                    if (tmpCourseMap.get(course2) == null) {
                                        tmpCourseMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else if (l == 3) {
                            String course1 = "";
                            String course2 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l - 1] != null) {
                                course1 = gS[i].classSchedule[j].daySchedule[k].schedule[l - 1].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course1, course2)) {
                                    if (tmpCourseMap.get(course2) == null) {
                                        tmpCourseMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else if (l == 4) {
                            String course2 = "";
                            String course3 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l + 1] != null) {
                                course3 = gS[i].classSchedule[j].daySchedule[k].schedule[l + 1].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course2, course3)) {
                                    if (tmpCourseMap.get(course2) == null) {
                                        tmpCourseMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else if (l == 7) {
                            String course1 = "";
                            String course2 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l - 1] != null) {
                                course1 = gS[i].classSchedule[j].daySchedule[k].schedule[l - 1].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course1, course2)) {
                                    if (tmpCourseMap.get(course2) == null) {
                                        tmpCourseMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else {
                            String course1 = "";
                            String course2 = "";
                            String course3 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l - 1] != null) {
                                course1 = gS[i].classSchedule[j].daySchedule[k].schedule[l - 1].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l + 1] != null) {
                                course3 = gS[i].classSchedule[j].daySchedule[k].schedule[l + 1].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course1, course2)
                                        || StringUtil.compareValue(course2, course3)) {
                                    if (tmpCourseMap.get(course2) == null) {
                                        tmpCourseMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                    }
                }
                if (tmpCourseMap != null) {
                    Iterator<Map.Entry<String, Integer>> tmpCourseMapEntries = tmpCourseMap.entrySet().iterator();
                    while (tmpCourseMapEntries.hasNext()) {
                        Map.Entry<String, Integer> entry = tmpCourseMapEntries.next();
                        allClassCourseMap.put(entry.getKey(), allClassCourseMap.get(entry.getKey()) + 1);


                    }
                }
            }


            tmpCCourseNum.put(tmpGrade, allClassCourseMap);


        }


        for (int i = 0; i < scheduleGrade.size(); i++) {
            Map<String, Integer> idealCC = gradeCoupletCourse.get(scheduleGrade.get(i));
            Map<String, Integer> realCC = tmpCCourseNum.get(scheduleGrade.get(i));
            Map<String, Integer> thisGradeCourseInfo = classHasCourse.get(scheduleGrade.get(i));
            if (realCC != null) {
                Iterator<Map.Entry<String, Integer>> idealCCEntries = idealCC.entrySet().iterator();
                while (idealCCEntries.hasNext()) {
                    Map.Entry<String, Integer> entry = idealCCEntries.next();
                    if (realCC.get(entry.getKey()) == thisGradeCourseInfo.get(entry.getKey())) {
                        totalScore = totalScore + 1;
                    }
                }
            }
        }
        return totalScore;
    }


    /**
     * 联堂课冲突检测/排除
     * 
     * @date 2015-6-23
     * @param individual
     * @return int
     */
    private GradeSchedule[] ccConflictResolution(GradeSchedule[] gradeSchedule, Map<String, EduGrade> gradeAndGradeId,
            Map<EduGrade, Map<String, Integer>> gradeCoupletCourse, List<EduGrade> scheduleGrade,
            Map<String, EduClass> classAndClassId, List<CourseTeacher> scheduleJoint, List<EduStaff> scheduleStaff,
            Map<EduClass, ClassScheduleConf> scheduleConf, List<Code> courseList, int advConf,
            Map<EduStaff, String> groupStaffCourseTime) {


        // 计算每个年级班级每门课联堂课次数
        GradeSchedule[] gS = gradeSchedule;
        Map<EduGrade, Map<EduClass, Map<String, Integer>>> tmpCCourseNum = new HashMap<EduGrade, Map<EduClass, Map<String, Integer>>>();
        for (int i = 0; i < gS.length; i++) {
            // 年级Map
            EduGrade tmpGrade = gradeAndGradeId.get(gS[i].gradeId);
            Map<EduClass, Map<String, Integer>> gradeMap = new HashMap<EduClass, Map<String, Integer>>();
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                // 班级Map
                EduClass tmpClass = classAndClassId.get(gS[i].classSchedule[j].classId);
                Map<String, Integer> tmpClassMap = new HashMap<String, Integer>();
                Map<String, Integer> allClassCourseMap = new HashMap<String, Integer>();
                for (int courseCount = 0; courseCount < courseList.size(); courseCount++) {
                    allClassCourseMap.put(courseList.get(courseCount).getCodeValue().toString(), 0);
                }
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (l == 0) {
                            String course2 = "";
                            String course3 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l + 1] != null) {
                                course3 = gS[i].classSchedule[j].daySchedule[k].schedule[l + 1].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course2, course3)) {
                                    if (tmpClassMap.get(course2) == null) {
                                        tmpClassMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else if (l == 3) {
                            String course1 = "";
                            String course2 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l - 1] != null) {
                                course1 = gS[i].classSchedule[j].daySchedule[k].schedule[l - 1].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course1, course2)) {
                                    if (tmpClassMap.get(course2) == null) {
                                        tmpClassMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else if (l == 4) {
                            String course2 = "";
                            String course3 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l + 1] != null) {
                                course3 = gS[i].classSchedule[j].daySchedule[k].schedule[l + 1].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course2, course3)) {
                                    if (tmpClassMap.get(course2) == null) {
                                        tmpClassMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else if (l == 7) {
                            String course1 = "";
                            String course2 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l - 1] != null) {
                                course1 = gS[i].classSchedule[j].daySchedule[k].schedule[l - 1].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course1, course2)) {
                                    if (tmpClassMap.get(course2) == null) {
                                        tmpClassMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                        else {
                            String course1 = "";
                            String course2 = "";
                            String course3 = "";
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l - 1] != null) {
                                course1 = gS[i].classSchedule[j].daySchedule[k].schedule[l - 1].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                                course2 = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            }
                            if (gS[i].classSchedule[j].daySchedule[k].schedule[l + 1] != null) {
                                course3 = gS[i].classSchedule[j].daySchedule[k].schedule[l + 1].courseId;
                            }
                            if (course2 != "") {


                                if (StringUtil.compareValue(course1, course2)
                                        || StringUtil.compareValue(course2, course3)) {
                                    if (tmpClassMap.get(course2) == null) {
                                        tmpClassMap.put(course2, 1);
                                    }
                                }
                            }
                        }
                    }
                }
                if (tmpClassMap != null) {
                    Iterator<Map.Entry<String, Integer>> tmpCourseMapEntries = tmpClassMap.entrySet().iterator();
                    while (tmpCourseMapEntries.hasNext()) {
                        Map.Entry<String, Integer> entry = tmpCourseMapEntries.next();
                        allClassCourseMap.put(entry.getKey(), 1);


                    }
                }
                gradeMap.put(tmpClass, allClassCourseMap);
            }
            tmpCCourseNum.put(tmpGrade, gradeMap);
        }
        // 统计教师每个时间段上课数
        Map<EduStaff, Map<String, List<EduClass>>> staffDuplicateCourse = new HashMap<EduStaff, Map<String, List<EduClass>>>();
        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {
            Map<String, List<EduClass>> tempMap = new HashMap<String, List<EduClass>>();
            for (int i = 0; i < DAY_COUNT; i++) {
                for (int j = 0; j < LECTURE_COUNT; j++) {
                    String tempString = i + "," + j;
                    tempMap.put(tempString, new ArrayList<EduClass>());
                }
            }
            staffDuplicateCourse.put(scheduleStaff.get(staffNum), tempMap);
        }
        for (int i = 0; i < gS.length; i++) {
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                            String classId = gS[i].classSchedule[j].classId;
                            String courseId = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            EduStaff TempTeacher = getTempTeacherByClassIdCourseId(classId, courseId, scheduleJoint);
                            Map<String, List<EduClass>> tempMap = staffDuplicateCourse.get(TempTeacher);
                            String key = k + "," + l;
                            List<EduClass> tempClassList = tempMap.get(key);
                            tempClassList.add(classAndClassId.get(classId));
                            tempMap.put(key, tempClassList);
                        }
                    }
                }
            }
        }


        // 获得每个时间段空闲教师
        Map<String, List<EduStaff>> freeTeacher = new HashMap<String, List<EduStaff>>();


        String[] keyList = new String[56];
        int count = 0;
        for (int i = 0; i < DAY_COUNT; i++) {
            for (int j = 0; j < LECTURE_COUNT; j++) {
                keyList[count] = i + "," + j;
                count++;
            }
        }


        for (int i = 0; i < keyList.length; i++) {
            freeTeacher.put(keyList[i], new ArrayList<EduStaff>());
        }


        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {


            Map<String, List<EduClass>> tempTeacherSchedule = staffDuplicateCourse.get(scheduleStaff.get(staffNum));


            for (int i = 0; i < keyList.length; i++) {
                if (tempTeacherSchedule.get(keyList[i]).size() == 0) {
                    List<EduStaff> tempTeacherList = freeTeacher.get(keyList[i]);
                    tempTeacherList.add(scheduleStaff.get(staffNum));
                    freeTeacher.put(keyList[i], tempTeacherList);
                }
            }


        }
        // 冲突检测 排除


        for (int i = 0; i < gS.length; i++) {
            EduGrade tmpGrade = gradeAndGradeId.get(gS[i].gradeId);
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                EduClass tmpClass = classAndClassId.get(gS[i].classSchedule[j].classId);
                Map<String, Integer> idealCC = gradeCoupletCourse.get(tmpGrade);
                Map<String, Integer> realCC = tmpCCourseNum.get(tmpGrade).get(tmpClass);
                if (realCC != null) {
                    Iterator<Map.Entry<String, Integer>> idealCCEntries = idealCC.entrySet().iterator();
                    while (idealCCEntries.hasNext()) {
                        Map.Entry<String, Integer> entry = idealCCEntries.next();
                        // 如果未找到联堂课
                        if (realCC.get(entry.getKey()) == 0
                                && (scheduleConf.get(tmpClass).courseTime.get(Integer.parseInt(entry.getKey())) != null)) {
                            // 解决标志
                            int rFlag = 1;
                            // 该门课课程码
                            String thisCourse = entry.getKey();
                            // 该课CourseTeacher
                            CourseTeacher thisCT = findCourseTeacherFromCTList(scheduleJoint,
                                    gS[i].classSchedule[j].classId, Integer.parseInt(thisCourse));
                            // 该教师
                            EduStaff thisTeacher = thisCT.getStaff();
                            // 教研组不排课参数
                            int[] groupConf = new int[2];
                            // 是否同时进行教研组不排课设置
                            if (advConf == 1) {
                                // 该教师是否在教研组不排课清单中
                                if (groupStaffCourseTime.containsKey(thisTeacher)) {
                                    groupConf[0] = Integer
                                            .parseInt(groupStaffCourseTime.get(thisTeacher).split("#")[0]);
                                    groupConf[1] = Integer
                                            .parseInt(groupStaffCourseTime.get(thisTeacher).split("#")[1]);
                                }


                            }
                            // 得到所有该门课的上课时间
                            List<String> courseTime = new ArrayList<String>();
                            for (int dayCount = 0; dayCount < DAY_COUNT; dayCount++) {
                                for (int lectureCount = 0; lectureCount < LECTURE_COUNT; lectureCount++) {
                                    if (groupConf[0] != 0) {
                                        if (LECTURE_COUNT != (groupConf[0] - 1)
                                                && ((DAY_COUNT / 4) != (groupConf[1] - 1))) {
                                            if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount] != null) {
                                                if (StringUtil
                                                        .compareValue(
                                                                gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount].courseId,
                                                                thisCourse)) {
                                                    courseTime.add((dayCount) + "," + lectureCount);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            // 得到所有该门课的上下节课的上课时间(如果有)
                            List<String[]> prvNextCourseTime = new ArrayList<String[]>();
                            for (int dayCount = 0; dayCount < DAY_COUNT; dayCount++) {
                                for (int lectureCount = 0; lectureCount < LECTURE_COUNT; lectureCount++) {
                                    if (groupConf[0] != 0) {
                                        if (LECTURE_COUNT != (groupConf[0] - 1)
                                                && ((DAY_COUNT / 4) != (groupConf[1] - 1))) {
                                            if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount] != null) {
                                                if (StringUtil
                                                        .compareValue(
                                                                gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount].courseId,
                                                                thisCourse)) {
                                                    if (lectureCount == 0) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1] != null) {


                                                            String nextCourse = (dayCount) + "," + (lectureCount + 1);
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[1] = nextCourse;
                                                            prvNextCourseTime.add(pNCourseString);
                                                        }
                                                    }
                                                    else if (lectureCount == 4) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1] != null) {


                                                            String nextCourse = (dayCount) + "," + (lectureCount + 1);
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[1] = nextCourse;
                                                            prvNextCourseTime.add(pNCourseString);
                                                        }
                                                    }
                                                    else if (lectureCount == 3) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1] != null) {


                                                            String prvCourse = (dayCount) + "," + (lectureCount - 1);
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[0] = prvCourse;
                                                            prvNextCourseTime.add(pNCourseString);
                                                        }
                                                    }
                                                    else if (lectureCount == 7) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1] != null) {


                                                            String prvCourse = (dayCount) + "," + (lectureCount - 1);
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[0] = prvCourse;
                                                            prvNextCourseTime.add(pNCourseString);
                                                        }
                                                    }
                                                    else {
                                                        String[] pNCourseString = new String[2];
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1] != null) {


                                                            String prvCourse = (dayCount) + "," + (lectureCount - 1);


                                                            pNCourseString[0] = prvCourse;
                                                        }
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1] != null) {


                                                            String nextCourse = (dayCount) + "," + (lectureCount + 1);


                                                            pNCourseString[1] = nextCourse;
                                                        }
                                                        prvNextCourseTime.add(pNCourseString);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            // 得到所有该门课的上下节课的课程（如果有）
                            List<String[]> prvNextCourseCode = new ArrayList<String[]>();
                            for (int dayCount = 0; dayCount < DAY_COUNT; dayCount++) {
                                for (int lectureCount = 0; lectureCount < LECTURE_COUNT; lectureCount++) {
                                    if (groupConf[0] != 0) {
                                        if (LECTURE_COUNT != (groupConf[0] - 1)
                                                && ((DAY_COUNT / 4) != (groupConf[1] - 1))) {
                                            if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount] != null) {
                                                if (StringUtil
                                                        .compareValue(
                                                                gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount].courseId,
                                                                thisCourse)) {
                                                    if (lectureCount == 0) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1] != null) {


                                                            String nextCourse = gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1].courseId;
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[1] = nextCourse;
                                                            prvNextCourseCode.add(pNCourseString);
                                                        }
                                                    }
                                                    else if (lectureCount == 4) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1] != null) {


                                                            String nextCourse = gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1].courseId;
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[1] = nextCourse;
                                                            prvNextCourseCode.add(pNCourseString);
                                                        }
                                                    }
                                                    else if (lectureCount == 3) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1] != null) {


                                                            String prvCourse = gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1].courseId;
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[0] = prvCourse;
                                                            prvNextCourseCode.add(pNCourseString);
                                                        }
                                                    }
                                                    else if (lectureCount == 7) {
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1] != null) {


                                                            String prvCourse = gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1].courseId;
                                                            String[] pNCourseString = new String[2];
                                                            pNCourseString[0] = prvCourse;
                                                            prvNextCourseCode.add(pNCourseString);
                                                        }
                                                    }
                                                    else {
                                                        String[] pNCourseString = new String[2];
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1] != null) {


                                                            String prvCourse = gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount - 1].courseId;


                                                            pNCourseString[0] = prvCourse;
                                                        }
                                                        if (gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1] != null) {


                                                            String nextCourse = gS[i].classSchedule[j].daySchedule[dayCount].schedule[lectureCount + 1].courseId;


                                                            pNCourseString[1] = nextCourse;
                                                        }
                                                        prvNextCourseCode.add(pNCourseString);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (prvNextCourseTime.size() != prvNextCourseCode.size()) {
                                System.out.println("222");
                            }
                            // 检测所有该门课的上下节课a，联堂课的老师是否有空。如果有空，a原教师的空闲是否与联堂课老师所带该班课程有交集
                            for (int pnCourseCount = 0; pnCourseCount < prvNextCourseTime.size(); pnCourseCount++) {
                                // 如果前一节课不为空，则查找前一节课的空闲老师
                                if (prvNextCourseTime.get(pnCourseCount)[0] != null) {
                                    List<EduStaff> tmpFTList = freeTeacher.get(prvNextCourseTime.get(pnCourseCount)[0]);
                                    // 如果空闲教师列表不为空
                                    if (tmpFTList != null) {
                                        // 如果空闲教师列表中有该教师a，则获取前一节课的教师b
                                        if (tmpFTList.contains(thisTeacher)) {
                                            EduStaff readyTeacher = findCourseTeacherFromCTList(scheduleJoint,
                                                    gS[i].classSchedule[j].classId,
                                                    Integer.parseInt(prvNextCourseCode.get(pnCourseCount)[0]))
                                                    .getStaff();
                                            // 遍历该节课上课时间，判断空间教师列表是否有教师b
                                            for (int thisCourseCount = 0; thisCourseCount < courseTime.size(); thisCourseCount++) {
                                                List<EduStaff> tmpFTList2 = freeTeacher.get(courseTime
                                                        .get(thisCourseCount));
                                                if (tmpFTList2.contains(readyTeacher)) {
                                                    // 获得待调课程时间
                                                    String[] newOrder = prvNextCourseTime.get(pnCourseCount)[0]
                                                            .split(",");
                                                    int day = Integer.parseInt(newOrder[0]);
                                                    int courseNum = Integer.parseInt(newOrder[1]);
                                                    // 获得冲突课程时间
                                                    String[] oldOrder = courseTime.get(thisCourseCount).split(",");
                                                    int oldDay = Integer.parseInt(oldOrder[0]);
                                                    int oldCourseNum = Integer.parseInt(oldOrder[1]);
                                                    // 待调课程courseId
                                                    if (gS[i].classSchedule[j].daySchedule[day].schedule[courseNum] == null) {
                                                        System.out.println("1111");
                                                    }
                                                    String newCourseId = gS[i].classSchedule[j].daySchedule[day].schedule[courseNum].courseId;
                                                    // 冲突课程courseId
                                                    String oldCourseId = gS[i].classSchedule[j].daySchedule[oldDay].schedule[oldCourseNum].courseId;
                                                    // 对调
                                                    gS[i].classSchedule[j].daySchedule[day].schedule[courseNum].courseId = oldCourseId;
                                                    gS[i].classSchedule[j].daySchedule[oldDay].schedule[oldCourseNum].courseId = newCourseId;
                                                    gS[i].classSchedule[j].code = generateCode(gS[i].classSchedule[j],
                                                            scheduleConf.get(tmpClass));
                                                    rFlag = 0;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                // 如果后一节课不为空，则查找后一节课的空闲老师
                                else if (prvNextCourseTime.get(pnCourseCount)[0] != null) {
                                    List<EduStaff> tmpFTList = freeTeacher.get(prvNextCourseTime.get(pnCourseCount)[0]);
                                    // 如果空闲教师列表不为空
                                    if (tmpFTList != null) {
                                        // 如果空闲教师列表中有该教师a，则获取后一节课的教师b
                                        if (tmpFTList.contains(thisTeacher)) {
                                            EduStaff readyTeacher = findCourseTeacherFromCTList(scheduleJoint,
                                                    gS[i].classSchedule[j].classId,
                                                    Integer.parseInt(prvNextCourseCode.get(pnCourseCount)[0]))
                                                    .getStaff();
                                            // 遍历该节课上课时间，判断空间教师列表是否有教师b
                                            for (int thisCourseCount = 0; thisCourseCount < courseTime.size(); thisCourseCount++) {
                                                List<EduStaff> tmpFTList2 = freeTeacher.get(courseTime
                                                        .get(thisCourseCount));
                                                if (tmpFTList2.contains(readyTeacher)) {
                                                    // 获得待调课程时间
                                                    String[] newOrder = prvNextCourseTime.get(pnCourseCount)[0]
                                                            .split(",");
                                                    int day = Integer.parseInt(newOrder[0]);
                                                    int courseNum = Integer.parseInt(newOrder[1]);
                                                    // 获得冲突课程时间
                                                    String[] oldOrder = courseTime.get(thisCourseCount).split(",");
                                                    int oldDay = Integer.parseInt(oldOrder[0]);
                                                    int oldCourseNum = Integer.parseInt(oldOrder[1]);
                                                    // 待调课程courseId
                                                    if (gS[i].classSchedule[j].daySchedule[day].schedule[courseNum] == null) {
                                                        System.out.println("1111");
                                                    }
                                                    String newCourseId = gS[i].classSchedule[j].daySchedule[day].schedule[courseNum].courseId;
                                                    // 冲突课程courseId
                                                    String oldCourseId = gS[i].classSchedule[j].daySchedule[oldDay].schedule[oldCourseNum].courseId;
                                                    // 对调
                                                    gS[i].classSchedule[j].daySchedule[day].schedule[courseNum].courseId = oldCourseId;
                                                    gS[i].classSchedule[j].daySchedule[oldDay].schedule[oldCourseNum].courseId = newCourseId;
                                                    gS[i].classSchedule[j].code = generateCode(gS[i].classSchedule[j],
                                                            scheduleConf.get(tmpClass));
                                                    rFlag = 0;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (rFlag == 1) {
                                return null;
                            }
                        }
                    }
                }
            }
        }


        return gS;
    }


    /**
     * 冲突检测/排除
     * 
     * @date 2015-7-4
     * @param gradeSchedule
     * @param scheduleJoint
     * @param scheduleStaff
     * @param teacherClass
     * @param classAndClassId
     * @param scheduleConf
     * @param courseCodeMap
     * @return GradeSchedule[]
     */
    private GradeSchedule[] conflictResolution(GradeSchedule[] gradeSchedule, List<CourseTeacher> scheduleJoint,
            List<EduStaff> scheduleStaff, Map<EduStaff, List<EduClass>> teacherClass,
            Map<String, EduClass> classAndClassId, Map<EduClass, ClassScheduleConf> scheduleConf) {


        // 统计教师每个时间段上课数
        Map<EduStaff, Map<String, List<EduClass>>> staffDuplicateCourse = new HashMap<EduStaff, Map<String, List<EduClass>>>();
        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {
            Map<String, List<EduClass>> tempMap = new HashMap<String, List<EduClass>>();
            for (int i = 0; i < DAY_COUNT; i++) {
                for (int j = 0; j < LECTURE_COUNT; j++) {
                    String tempString = i + "," + j;
                    tempMap.put(tempString, new ArrayList<EduClass>());
                }
            }
            staffDuplicateCourse.put(scheduleStaff.get(staffNum), tempMap);
        }
        GradeSchedule[] gS = gradeSchedule;
        for (int i = 0; i < gS.length; i++) {
            for (int j = 0; j < gS[i].classSchedule.length; j++) {
                for (int k = 0; k < DAY_COUNT; k++) {
                    for (int l = 0; l < LECTURE_COUNT; l++) {
                        if (gS[i].classSchedule[j].daySchedule[k].schedule[l] != null) {
                            String classId = gS[i].classSchedule[j].classId;
                            String courseId = gS[i].classSchedule[j].daySchedule[k].schedule[l].courseId;
                            EduStaff TempTeacher = getTempTeacherByClassIdCourseId(classId, courseId, scheduleJoint);
                            Map<String, List<EduClass>> tempMap = staffDuplicateCourse.get(TempTeacher);
                            String key = k + "," + l;
                            List<EduClass> tempClassList = tempMap.get(key);
                            tempClassList.add(classAndClassId.get(classId));
                            tempMap.put(key, tempClassList);
                        }
                    }
                }
            }
        }


        // 获得每个时间段空闲教师
        Map<String, List<EduStaff>> freeTeacher = new HashMap<String, List<EduStaff>>();


        String[] keyList = new String[56];
        int count = 0;
        for (int i = 0; i < DAY_COUNT; i++) {
            for (int j = 0; j < LECTURE_COUNT; j++) {
                keyList[count] = i + "," + j;
                count++;
            }
        }


        for (int i = 0; i < keyList.length; i++) {
            freeTeacher.put(keyList[i], new ArrayList<EduStaff>());
        }


        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {


            Map<String, List<EduClass>> tempTeacherSchedule = staffDuplicateCourse.get(scheduleStaff.get(staffNum));


            for (int i = 0; i < keyList.length; i++) {
                if (tempTeacherSchedule.get(keyList[i]).size() == 0) {
                    List<EduStaff> tempTeacherList = freeTeacher.get(keyList[i]);
                    tempTeacherList.add(scheduleStaff.get(staffNum));
                    freeTeacher.put(keyList[i], tempTeacherList);
                }
            }


        }


        // 冲突检测/排除
        for (int staffNum = 0; staffNum < scheduleStaff.size(); staffNum++) {


            Map<String, List<EduClass>> tempTeacherHaveClass = staffDuplicateCourse.get(scheduleStaff.get(staffNum));
            for (int i = 0; i < keyList.length; i++) {
                List<EduClass> tempClass = tempTeacherHaveClass.get(keyList[i]);
                // 如果存在冲突
                if (tempClass.size() > 1) {
                    // 获取冲突的第一个班级
                    EduClass thisClass = tempClass.get(0);
                    // 获取该冲突时间空闲的教师
                    List<EduStaff> thisFreeTeacher = freeTeacher.get(keyList[i]);
                    // 设置冲突解决flag
                    int doneFlag = 0;
                    // 逐个检测空闲教师所带班级是否有该班
                    for (int teacherCount = 0; teacherCount < thisFreeTeacher.size(); teacherCount++) {
                        if (doneFlag == 0) {
                            // 若为该班教师
                            if (teacherClass.get(thisFreeTeacher.get(teacherCount)).contains(thisClass)) {
                                // 获取该教师上课安排Map


                                Map<String, List<EduClass>> otherTeacherCourseList = staffDuplicateCourse
                                        .get(thisFreeTeacher.get(teacherCount));
                                // 获取该教师该班级的上课安排
                                List<String> otherTeacherSchedule = new ArrayList<String>();
                                for (int keyListLength = 0; keyListLength < keyList.length; keyListLength++) {
                                    if (otherTeacherCourseList.get(keyList[keyListLength]).contains(thisClass)) {
                                        otherTeacherSchedule.add(keyList[keyListLength]);
                                    }
                                }
                                // 获得待调课程时间
                                Random rand = new Random();
                                int randCourseNum = rand.nextInt(otherTeacherSchedule.size());
                                String[] newOrder = otherTeacherSchedule.get(randCourseNum).split(",");
                                int day = Integer.parseInt(newOrder[0]);
                                int courseNum = Integer.parseInt(newOrder[1]);
                                // 获得冲突课程时间
                                String[] oldOrder = keyList[i].split(",");
                                int oldDay = Integer.parseInt(oldOrder[0]);
                                int oldCourseNum = Integer.parseInt(oldOrder[1]);


                                // 调换课程
                                for (int gradeCount = 0; gradeCount < gS.length; gradeCount++) {
                                    if (StringUtil.compareValue(gS[gradeCount].gradeId, thisClass.getNj().getGradeId())) {
                                        for (int classCount = 0; classCount < gS[gradeCount].classSchedule.length; classCount++) {
                                            if (StringUtil.compareValue(
                                                    gS[gradeCount].classSchedule[classCount].classId,
                                                    thisClass.getClassId())) {
                                                // 待调课程courseId
                                                String newCourseId = gS[gradeCount].classSchedule[classCount].daySchedule[day].schedule[courseNum].courseId;
                                                // 冲突课程courseId
                                                String oldCourseId = gS[gradeCount].classSchedule[classCount].daySchedule[oldDay].schedule[oldCourseNum].courseId;
                                                // 对调
                                                gS[gradeCount].classSchedule[classCount].daySchedule[day].schedule[courseNum].courseId = oldCourseId;
                                                gS[gradeCount].classSchedule[classCount].daySchedule[oldDay].schedule[oldCourseNum].courseId = newCourseId;
                                                gS[gradeCount].classSchedule[classCount].code = generateCode(


                                                gS[gradeCount].classSchedule[classCount], scheduleConf.get(thisClass));


                                            }
                                        }
                                    }
                                }
                                doneFlag = 1;
                            }
                        }
                    }
                    if (doneFlag == 0) {
                        return null;
                    }
                }
            }
        }


        return gS;
    }


    /**
     * 生成基因
     * 
     * @param classSchedule
     * @param thisScheduleConf
     * @return
     */
    private String generateCode(ClassSchedule classSchedule, ClassScheduleConf thisScheduleConf) {


        String code = "";
        for (int day = 0; day < DAY_COUNT; day++) {
            for (int lectureNum = 0; lectureNum < LECTURE_COUNT; lectureNum++) {
                if (thisScheduleConf.haveClass[day][lectureNum] == 1) {
                    code = code + classSchedule.daySchedule[day].schedule[lectureNum].courseId;
                }
            }
        }
        return code;
    }


    /**
     * 从CTList中寻找CourseTeacher
     * 
     * @param scheduleJoint
     * @param classId
     * @param course
     * @return
     */
    private CourseTeacher findCourseTeacherFromCTList(List<CourseTeacher> scheduleJoint, String classId, int course) {
        int flag = 0;// 是否找到标志
        int index = 0; // 计数
        CourseTeacher result = new CourseTeacher();
        while (flag == 0) {
            if (StringUtil.compareValue(scheduleJoint.get(index).getEduClass().getClassId(), classId)
                    && (scheduleJoint.get(index).getCoursePlan().getCourseCode() == course)) {
                result = scheduleJoint.get(index);
                flag = 1;
            }
            index++;
        }
        return result;
    }


    // 单节课安排
    public class Schedule {


        String courseId;
        int num;


        public String getCourseId() {


            return courseId;
        }


        public void setCourseId(String courseId) {


            this.courseId = courseId;
        }


        public int getNum() {


            return num;
        }


        public void setNum(int num) {


            this.num = num;
        }
    }


    // 日安排
    public class DaySchedule {


        int day;
        Schedule[] schedule = new Schedule[LECTURE_COUNT];


        public int getDay() {


            return day;
        }


        public void setDay(int day) {


            this.day = day;
        }


        public Schedule[] getSchedule() {


            return schedule;
        }


        public void setSchedule(Schedule[] schedule) {


            this.schedule = schedule;
        }


    }


    // 班级安排
    public class ClassSchedule {


        String classId;
        DaySchedule[] daySchedule = new DaySchedule[DAY_COUNT];
        String code = "";


        public String getClassId() {


            return classId;
        }


        public void setClassId(String classId) {


            this.classId = classId;
        }


        public DaySchedule[] getDaySchedule() {


            return daySchedule;
        }


        public void setDaySchedule(DaySchedule[] daySchedule) {


            this.daySchedule = daySchedule;
        }


        public String getCode() {


            return code;
        }


        public void setCode(String code) {


            this.code = code;
        }
    }


    // 年级安排
    public class GradeSchedule {


        String gradeId;
        ClassSchedule[] classSchedule;


        public String getGradeId() {


            return gradeId;
        }


        public void setGradeId(String gradeId) {


            this.gradeId = gradeId;
        }


        public ClassSchedule[] getClassSchedule() {


            return classSchedule;
        }


        public void setClassSchedule(ClassSchedule[] classSchedule) {


            this.classSchedule = classSchedule;
        }


    }


    // 个体
    public class Individual {


        GradeSchedule[] schedulePlan;
        int score;


        public GradeSchedule[] getSchedulePlan() {


            return schedulePlan;
        }


        public void setSchedulePlan(GradeSchedule[] schedulePlan) {


            this.schedulePlan = schedulePlan;
        }


        public int getScore() {


            return score;
        }


        public void setScore(int score) {


            this.score = score;
        }


    }


    // 班级排课参数
    public class ClassScheduleConf {


        int[][] haveClass;
        Map<Integer, Integer> courseTime;
        List<String> coursePool;


        public int[][] getHaveClass() {


            return haveClass;
        }


        public void setHaveClass(int[][] haveClass) {


            this.haveClass = haveClass;
        }


        public Map<Integer, Integer> getCourseTime() {


            return courseTime;
        }


        public void setCourseTime(Map<Integer, Integer> courseTime) {


            this.courseTime = courseTime;
        }


        public List<String> getCoursePool() {


            return coursePool;
        }


        public void setCoursePool(List<String> coursePool) {


            this.coursePool = coursePool;
        }
    }


    public XnXqServiceAware getXnXqService() {
        return xnXqService;
    }


    public void setXnXqService(XnXqServiceAware xnXqService) {
        this.xnXqService = xnXqService;
    }


    public CoursePlanServiceAware getCoursePlanService() {
        return coursePlanService;
    }


    public void setCoursePlanService(CoursePlanServiceAware coursePlanService) {
        this.coursePlanService = coursePlanService;
    }


    public CoursePlanDaoAware getCoursePlanDao() {
        return coursePlanDao;
    }


    public void setCoursePlanDao(CoursePlanDaoAware coursePlanDao) {
        this.coursePlanDao = coursePlanDao;
    }


    public EduScheduleDaoAware getEduScheduleDao() {
        return eduScheduleDao;
    }


    public void setEduScheduleDao(EduScheduleDaoAware eduScheduleDao) {
        this.eduScheduleDao = eduScheduleDao;
    }


    public EduGradeServiceAware getEduGradeService() {
        return eduGradeService;
    }


    public void setEduGradeService(EduGradeServiceAware eduGradeService) {
        this.eduGradeService = eduGradeService;
    }
}



