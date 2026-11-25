"""Numerical fit shared by the Level 2 trainers."""
import numpy as np
from scipy.optimize import minimize
from scipy.special import expit


def fit_logistic_parameters(matrix, labels, penalty, *, max_iterations=None):
    centers = matrix.mean(axis=0)
    scales = matrix.std(axis=0)
    scales[scales < 1e-12] = 1
    design = (matrix - centers) / scales

    def objective(parameters):
        weights, intercept = parameters[:-1], parameters[-1]
        logits = design @ weights + intercept
        loss = np.mean(np.logaddexp(0, logits) - labels * logits) + penalty * np.dot(weights, weights) / 2
        residual = expit(logits) - labels
        gradient = np.append(design.T @ residual / len(labels) + penalty * weights, residual.mean())
        return loss, gradient

    options = None if max_iterations is None else {"maxiter": max_iterations}
    fit = minimize(objective, np.zeros(matrix.shape[1] + 1), method="L-BFGS-B", jac=True, options=options)
    return centers, scales, fit
