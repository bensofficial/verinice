package sernet.gs.reveng;

// Generated Jun 5, 2015 1:28:34 PM by Hibernate Tools 3.4.0.CR1

import java.util.List;

import javax.naming.InitialContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.LockMode;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Example;

/**
 * Home object for domain model class StgMbMassPhaseTxt.
 * @see sernet.gs.reveng.StgMbMassPhaseTxt
 * @author Hibernate Tools
 */
public class StgMbMassPhaseTxtHome {

	private static final Log log = LogFactory
			.getLog(StgMbMassPhaseTxtHome.class);

	private final SessionFactory sessionFactory = getSessionFactory();

	protected SessionFactory getSessionFactory() {
		try {
			return (SessionFactory) new InitialContext()
					.lookup("SessionFactory");
		} catch (Exception e) {
			log.error("Could not locate SessionFactory in JNDI", e);
			throw new IllegalStateException(
					"Could not locate SessionFactory in JNDI");
		}
	}

	public void persist(StgMbMassPhaseTxt transientInstance) {
		log.debug("persisting StgMbMassPhaseTxt instance");
		try {
			sessionFactory.getCurrentSession().persist(transientInstance);
			log.debug("persist successful");
		} catch (RuntimeException re) {
			log.error("persist failed", re);
			throw re;
		}
	}

	public void attachDirty(StgMbMassPhaseTxt instance) {
		log.debug("attaching dirty StgMbMassPhaseTxt instance");
		try {
			sessionFactory.getCurrentSession().saveOrUpdate(instance);
			log.debug("attach successful");
		} catch (RuntimeException re) {
			log.error("attach failed", re);
			throw re;
		}
	}

	public void attachClean(StgMbMassPhaseTxt instance) {
		log.debug("attaching clean StgMbMassPhaseTxt instance");
		try {
			sessionFactory.getCurrentSession().lock(instance, LockMode.NONE);
			log.debug("attach successful");
		} catch (RuntimeException re) {
			log.error("attach failed", re);
			throw re;
		}
	}

	public void delete(StgMbMassPhaseTxt persistentInstance) {
		log.debug("deleting StgMbMassPhaseTxt instance");
		try {
			sessionFactory.getCurrentSession().delete(persistentInstance);
			log.debug("delete successful");
		} catch (RuntimeException re) {
			log.error("delete failed", re);
			throw re;
		}
	}

	public StgMbMassPhaseTxt merge(StgMbMassPhaseTxt detachedInstance) {
		log.debug("merging StgMbMassPhaseTxt instance");
		try {
			StgMbMassPhaseTxt result = (StgMbMassPhaseTxt) sessionFactory
					.getCurrentSession().merge(detachedInstance);
			log.debug("merge successful");
			return result;
		} catch (RuntimeException re) {
			log.error("merge failed", re);
			throw re;
		}
	}

	public StgMbMassPhaseTxt findById(sernet.gs.reveng.StgMbMassPhaseTxtId id) {
		log.debug("getting StgMbMassPhaseTxt instance with id: " + id);
		try {
			StgMbMassPhaseTxt instance = (StgMbMassPhaseTxt) sessionFactory
					.getCurrentSession().get(
							"sernet.gs.reveng.StgMbMassPhaseTxt", id);
			if (instance == null) {
				log.debug("get successful, no instance found");
			} else {
				log.debug("get successful, instance found");
			}
			return instance;
		} catch (RuntimeException re) {
			log.error("get failed", re);
			throw re;
		}
	}

	public List findByExample(StgMbMassPhaseTxt instance) {
		log.debug("finding StgMbMassPhaseTxt instance by example");
		try {
			List results = sessionFactory.getCurrentSession()
					.createCriteria("sernet.gs.reveng.StgMbMassPhaseTxt")
					.add(Example.create(instance)).list();
			log.debug("find by example successful, result size: "
					+ results.size());
			return results;
		} catch (RuntimeException re) {
			log.error("find by example failed", re);
			throw re;
		}
	}
}
